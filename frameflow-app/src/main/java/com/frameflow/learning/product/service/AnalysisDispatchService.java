package com.frameflow.learning.product.service;

import java.util.ArrayList;
import java.util.List;

import com.frameflow.learning.product.mq.AnalysisTaskMessage;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.product.repo.AnalysisOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final FrameFlowMetrics metrics;
    private final ProgressCacheService progressCache;
    private final AnalysisOutboxMapper outbox;
    private final ObjectMapper objectMapper;
    private final AnalysisOutboxRelay outboxRelay;

    public AnalysisDispatchService(BatchService batchService, CandidateMapper candidates,
                                   AnalysisRunMapper runs, QualityProfileMapper profiles,
                                   com.frameflow.learning.product.repo.BriefMapper briefs,
                                   FrameFlowMetrics metrics, ProgressCacheService progressCache,
                                   AnalysisOutboxMapper outbox, ObjectMapper objectMapper,
                                   AnalysisOutboxRelay outboxRelay) {
        this.batchService = batchService;
        this.candidates = candidates;
        this.runs = runs;
        this.profiles = profiles;
        this.briefs = briefs;
        this.metrics = metrics;
        this.progressCache = progressCache;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.outboxRelay = outboxRelay;
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
            AnalysisTaskMessage message = new AnalysisTaskMessage(
                    runId, candidate.getId(), batchId,
                    candidate.getObjectKey(), candidate.getContentType(),
                    candidate.getSizeBytes(), version.getSpecJson(),
                    briefContentOf(batch), 1);
            try {
                outbox.insert(runId, objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "无法序列化分析任务");
            }
            metrics.recordDispatch(FrameFlowMetrics.DispatchOutcome.CONFIRMED);
            log.info("已写入分析 outbox runId={} candidateId={}", runId, candidate.getId());
        }
        // ★ 核心：先落 outbox 再提交事务，提交后才发 MQ。
        // 避免「broker 已收到、DB 回滚」让 worker 回写 404。
        if (!dispatchedIds.isEmpty()) {
            progressCache.evict(batchId);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        outboxRelay.publishPending();
                    }
                });
            } else {
                outboxRelay.publishPending();
            }
        }
        return new DispatchResult(batchId, dispatchedIds.size(), 0);
    }
}
