package com.frameflow.learning.product.service;

import java.util.List;

import com.frameflow.learning.product.repo.FindingMapper;
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

    public AnalysisQueryService(FindingMapper findings, BatchService batchService,
                                AnalysisDispatchService dispatchService) {
        this.findings = findings;
        this.batchService = batchService;
        this.dispatchService = dispatchService;
    }

    public List<FindingResponse> findingsOf(long userId, long candidateId) {
        long batchId = dispatchService.candidateBatchId(candidateId);
        batchService.requireBatchOfMyTeam(userId, batchId);
        return findings.listByCandidate(candidateId).stream()
                .map(f -> new FindingResponse(f.getId(), f.getDimension(), f.getDetector(),
                        f.getDetectorVersion(), f.isPassed(), f.getSeverity(),
                        f.getTimecodeMs(), f.getEvidence(), f.getMessage()))
                .toList();
    }
}
