package com.frameflow.learning.product.service;

import java.util.List;

import com.frameflow.learning.product.repo.FindingMapper;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.product.web.AnalysisDtos.FindingResponse;
import org.springframework.stereotype.Service;

/**
 * Finding 查询服务——接口层不直接触碰数据层（ArchitectureTest 的
 * web 不依赖 repo 规则强制；本类正是开发中被该规则抓到后抽出来的，
 * 连返回类型也不能引用 repo 的行对象，须在服务层转成接口 DTO）。
 */
@Service
public class AnalysisQueryService {

    private final FindingMapper findings;
    private final BatchService batchService;
    private final AnalysisDispatchService dispatchService;
    private final com.frameflow.learning.product.repo.CandidateMapper candidates;
    private final StoragePort storage;
    private final java.time.Clock clock;

    public AnalysisQueryService(FindingMapper findings, BatchService batchService,
                                AnalysisDispatchService dispatchService,
                                com.frameflow.learning.product.repo.CandidateMapper candidates,
                                StoragePort storage, java.time.Clock clock) {
        this.findings = findings;
        this.batchService = batchService;
        this.dispatchService = dispatchService;
        this.candidates = candidates;
        this.storage = storage;
        this.clock = clock;
    }

    /** F8：审阅页播放地址——短时效 presigned GET（15 分钟）。 */
    public com.frameflow.learning.product.web.AnalysisDtos.ContentUrlResponse contentUrlOf(
            long userId, long candidateId) {
        var candidate = candidates.findById(candidateId);
        if (candidate == null) {
            throw new com.frameflow.learning.shared.error.ApiException(
                    com.frameflow.learning.shared.error.ErrorCode.RESOURCE_NOT_FOUND);
        }
        batchService.requireBatchOfMyTeam(userId, candidate.getBatchId());
        java.time.Duration ttl = java.time.Duration.ofMinutes(15);
        return new com.frameflow.learning.product.web.AnalysisDtos.ContentUrlResponse(
                storage.presignGet(candidate.getObjectKey(), ttl),
                java.time.OffsetDateTime.now(clock).plus(ttl).toString());
    }

    public List<FindingResponse> findingsOf(long userId, long candidateId) {
        long batchId = dispatchService.candidateBatchId(candidateId);
        batchService.requireBatchOfMyTeam(userId, batchId);
        return findings.listByCandidate(candidateId).stream()
                .map(f -> new FindingResponse(f.getId(), f.getDimension(), f.getDetector(),
                        f.getDetectorVersion(), f.isPassed(), f.getSeverity(),
                        f.getTimecodeMs(), f.getEvidence(), f.getMessage(), f.getVerdict()))
                .toList();
    }
}
