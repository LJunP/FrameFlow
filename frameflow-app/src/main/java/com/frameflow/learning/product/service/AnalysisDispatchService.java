package com.frameflow.learning.product.service;

import java.util.ArrayList;
import java.util.List;

import com.frameflow.learning.product.mq.AnalysisTaskMessage;
import com.frameflow.learning.product.mq.RabbitConfig;
import com.frameflow.learning.product.repo.AnalysisRunMapper;
import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.CandidateRow;
import com.frameflow.learning.product.repo.QualityProfileMapper;
import com.frameflow.learning.product.repo.QualityProfileVersionRow;
import com.frameflow.learning.observability.FrameFlowMetrics;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1 批次调度：为全部 UPLOADED 候选建立 run 并派发到 MQ。
 */
@Service
public class AnalysisDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisDispatchService.class);

    private final BatchService batchService;
    private final CandidateMapper candidates;
    private final AnalysisRunMapper runs;
    private final QualityProfileMapper profiles;
    private final com.frameflow.learning.product.repo.BriefMapper briefs;
    private final RabbitTemplate rabbitTemplate;
    private final FrameFlowMetrics metrics;

    public AnalysisDispatchService(BatchService batchService, CandidateMapper candidates,
                                   AnalysisRunMapper runs, QualityProfileMapper profiles,
                                   com.frameflow.learning.product.repo.BriefMapper briefs,
                                   RabbitTemplate rabbitTemplate, FrameFlowMetrics metrics) {
        this.batchService = batchService;
        this.candidates = candidates;
        this.runs = runs;
        this.profiles = profiles;
        this.briefs = briefs;
        this.rabbitTemplate = rabbitTemplate;
        this.metrics = metrics;
    }

    public record DispatchResult(long batchId, int dispatched, int skipped) {
    }

    /** 供接口层做归属校验：候选 → 批次。 */
    public long candidateBatchId(long candidateId) {
        CandidateRow candidate = candidates.findById(candidateId);
        if (candidate == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return candidate.getBatchId();
    }

    private String briefContentOf(com.frameflow.learning.product.repo.BatchRow batch) {
        return briefs.findById(batch.getBriefId()).getContent();
    }

    @Transactional
    public DispatchResult dispatchBatch(long userId, long batchId) {
        var batch = batchService.requireWritableBatch(userId, batchId);
        if (!"CLOSED".equals(batch.getStatus())) {
            throw new ApiException(ErrorCode.BATCH_CLOSED, "批次必须先关闭再触发分析");
        }
        QualityProfileVersionRow version =
                profiles.findVersionById(batch.getProfileVersionId());
        List<Long> dispatchedIds = new ArrayList<>();
        for (CandidateRow candidate : candidates.listByBatch(batchId)) {
            if (!"UPLOADED".equals(candidate.getStatus())) {
                continue;
            }
            // 条件更新当锁：并发点两次"分析"，只有一次能把候选推进 ANALYZING
            if (candidates.markAnalyzing(candidate.getId()) == 0) {
                continue;
            }
            Long runId = runs.insert(candidate.getId(), batchId);
            dispatchedIds.add(runId);
            // 消息自包含：worker 不需要回查任何接口就知道"检什么、按什么标准"
            publishConfirmed(new AnalysisTaskMessage(
                    runId, candidate.getId(), batchId,
                    candidate.getObjectKey(), candidate.getContentType(),
                    candidate.getSizeBytes(), version.getSpecJson(),
                    briefContentOf(batch), 1),
                    candidate.getId());
        }
        // 事务提交在方法返回时——publish 在事务内先行。若提交失败，
        // 消息可能已发出但 run 行不存在，worker 回写会得到 404 并重试后
        // 放弃（DLQ 留痕）。这是"至少一次投递"的正常代价，见导读 §3。
        return new DispatchResult(batchId, dispatchedIds.size(), 0);
    }

    /**
     * ★ 核心：Publisher Confirm——RabbitMQ 收到消息才返回。没有它，
     * broker 宕机时消息"发出即忘"，任务静默丢失且无从知晓。
     * waitForConfirmsOrDie 抛异常 → 事务回滚 → 候选停留 UPLOADED，
     * 可安全重新触发。
     */
    private void publishConfirmed(AnalysisTaskMessage message, long candidateId) {
        try {
            Boolean confirmed = rabbitTemplate.invoke(operation -> {
                operation.convertAndSend(
                        RabbitConfig.TASK_EXCHANGE, RabbitConfig.TASK_ROUTING_KEY, message);
                return operation.waitForConfirms(5_000);
            });
            if (!Boolean.TRUE.equals(confirmed)) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR,
                        "broker 未确认消息, candidateId=" + candidateId);
            }
            metrics.recordDispatch(FrameFlowMetrics.DispatchOutcome.CONFIRMED);
            log.info("已派发分析任务 runId={} candidateId={}", message.runId(), candidateId);
        } catch (RuntimeException exception) {
            metrics.recordDispatch(FrameFlowMetrics.DispatchOutcome.FAILED);
            throw exception;
        }
    }
}
