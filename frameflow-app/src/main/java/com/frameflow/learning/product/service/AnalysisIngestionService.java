package com.frameflow.learning.product.service;

import java.util.List;

import com.frameflow.learning.product.repo.AnalysisRunMapper;
import com.frameflow.learning.product.repo.AnalysisRunRow;
import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.FindingMapper;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T5 结果回写：worker 结论的唯一入口，幂等是它的第一属性。
 */
@Service
public class AnalysisIngestionService {

    private final AnalysisRunMapper runs;
    private final CandidateMapper candidates;
    private final FindingMapper findings;

    public AnalysisIngestionService(AnalysisRunMapper runs, CandidateMapper candidates,
                                    FindingMapper findings) {
        this.runs = runs;
        this.candidates = candidates;
        this.findings = findings;
    }

    public record FindingRequest(String detector, String detectorVersion, String dimension,
                                 boolean passed, String severity, Long timecodeMs,
                                 String evidence, String message) {
    }

    public record ResultRequest(long runId, String workerVersion, boolean ok,
                                String errorSummary, List<FindingRequest> findings,
                                Long durationMs, Integer width, Integer height, Double fps) {
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
            return new IngestionResponse(run.getId(), run.getCandidateId(),
                    candidates.findById(run.getCandidateId()).getStatus(), true);
        }

        if (!req.ok()) {
            // ★ 红线落地：worker 故障 = ANALYSIS_ERROR（系统问题），
            // 绝不写成 AUTO_REJECT（视频问题）。两者在报表里是两个世界。
            candidates.markAnalysisResult(run.getCandidateId(), "ANALYSIS_ERROR",
                    null, null, null, null);
            return new IngestionResponse(run.getId(), run.getCandidateId(),
                    "ANALYSIS_ERROR", false);
        }

        boolean blocker = false;
        for (FindingRequest f : req.findings()) {
            findings.insert(run.getId(), run.getCandidateId(), f.detector(),
                    f.detectorVersion(), f.dimension(), f.passed(), f.severity(),
                    f.timecodeMs(), f.evidence(), f.message());
            blocker |= !f.passed() && "BLOCKER".equals(f.severity());
        }
        String finalStatus = blocker ? "AUTO_REJECT" : "ANALYZED";
        candidates.markAnalysisResult(run.getCandidateId(), finalStatus,
                req.durationMs(), req.width(), req.height(), req.fps());
        return new IngestionResponse(run.getId(), run.getCandidateId(), finalStatus, false);
    }
}
