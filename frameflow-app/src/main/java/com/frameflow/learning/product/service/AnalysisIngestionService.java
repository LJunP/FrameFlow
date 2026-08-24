package com.frameflow.learning.product.service;

import java.util.List;
import java.util.Set;

import com.frameflow.learning.product.repo.AnalysisRunMapper;
import com.frameflow.learning.product.repo.AnalysisRunRow;
import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.FindingMapper;
import com.frameflow.learning.observability.FrameFlowMetrics;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T5 结果回写：worker 结论的唯一入口，幂等是它的第一属性。
 */
@Service
public class AnalysisIngestionService {

    private static final Set<String> SEMANTIC_VERDICTS =
            Set.of("PASS", "VIOLATE", "UNKNOWN", "ERROR");

    private final AnalysisRunMapper runs;
    private final CandidateMapper candidates;
    private final FindingMapper findings;
    private final ProgressCacheService progressCache;
    private final FrameFlowMetrics metrics;

    public AnalysisIngestionService(AnalysisRunMapper runs, CandidateMapper candidates,
                                    FindingMapper findings, ProgressCacheService progressCache,
                                    FrameFlowMetrics metrics) {
        this.runs = runs;
        this.candidates = candidates;
        this.findings = findings;
        this.progressCache = progressCache;
        this.metrics = metrics;
    }

    public record FindingRequest(String detector, String detectorVersion, String dimension,
                                 boolean passed, String severity, Long timecodeMs,
                                 String evidence, String message, String verdict) {
    }

    public record ResultRequest(long runId, String workerVersion, boolean ok,
                                String errorSummary, List<FindingRequest> findings,
                                Long durationMs, Integer width, Integer height, Double fps,
                                String contentHash, String phash) {
    }

    public record IngestionResponse(long runId, long candidateId, String candidateStatus,
                                    boolean duplicate) {
    }

    @Transactional
    public IngestionResponse ingest(ResultRequest req) {
        AnalysisRunRow run = runs.findById(req.runId());
        if (run == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "run 不存在");
        }
        // ★ 核心：条件状态迁移是幂等的裁决点——重复回放（网络重试、消息重投）
        // 到达时 run 已非 RUNNING，这里返回既有状态且【不产生任何新写入】。
        // 消息系统是"至少一次"投递，接收方幂等是标配而非优化。
        int updated = runs.finalizeIfRunning(
                req.runId(),
                req.ok() ? "SUCCEEDED" : "FAILED",
                req.workerVersion(),
                req.ok() ? null : req.errorSummary());
        if (updated == 0) {
            metrics.recordIngestion(FrameFlowMetrics.IngestionOutcome.DUPLICATE);
            return new IngestionResponse(run.getId(), run.getCandidateId(),
                    candidates.findById(run.getCandidateId()).getStatus(), true);
        }

        if (!req.ok()) {
            // ★ 红线落地：worker 故障 = ANALYSIS_ERROR（系统问题），
            // 绝不写成 AUTO_REJECT（视频问题）。两者在报表里是两个世界。
            candidates.markAnalysisResult(run.getCandidateId(), "ANALYSIS_ERROR",
                    null, null, null, null, null, null);
            progressCache.evict(run.getBatchId());
            metrics.recordIngestion(FrameFlowMetrics.IngestionOutcome.ANALYSIS_ERROR);
            return new IngestionResponse(run.getId(), run.getCandidateId(),
                    "ANALYSIS_ERROR", false);
        }

        boolean blocker = false;
        boolean needsReview = false;
        for (FindingRequest f : req.findings()) {
            // ★ 红线（docs/01 §8.2）：语义 Finding 禁止 BLOCKER——
            // 模型意见没有"生杀大权"。旧/异常 Worker 可能只带
            // detector=semantic 而遗漏 verdict，不能因此被当成确定性 BLOCKER。
            boolean semantic = "semantic".equals(f.detector()) || f.verdict() != null;
            String verdict = semantic && (f.verdict() == null
                    || !SEMANTIC_VERDICTS.contains(f.verdict()))
                    ? "ERROR" : f.verdict();
            String severity = semantic ? "WARNING" : f.severity();
            boolean passed = semantic ? "PASS".equals(verdict) : f.passed();
            findings.insert(run.getId(), run.getCandidateId(), f.detector(),
                    f.detectorVersion(), f.dimension(), passed, severity,
                    f.timecodeMs(), f.evidence(), f.message(), verdict);
            blocker |= !passed && "BLOCKER".equals(severity);
            // 语义 VIOLATE/UNKNOWN/ERROR 都进人工复核（REVIEW_REQUIRED）
            needsReview |= semantic && !"PASS".equals(verdict);
        }
        String finalStatus;
        if (blocker) {
            finalStatus = "AUTO_REJECT";           // 仅确定性规则可触发
        } else if (needsReview) {
            finalStatus = "REVIEW_REQUIRED";       // 语义存疑 → 人来定夺
        } else {
            finalStatus = "ANALYZED";
        }
        candidates.markAnalysisResult(run.getCandidateId(), finalStatus,
                req.durationMs(), req.width(), req.height(), req.fps(),
                req.contentHash(), req.phash());
        progressCache.evict(run.getBatchId());
        metrics.recordIngestion(FrameFlowMetrics.IngestionOutcome.SUCCEEDED);
        return new IngestionResponse(run.getId(), run.getCandidateId(), finalStatus, false);
    }
}
