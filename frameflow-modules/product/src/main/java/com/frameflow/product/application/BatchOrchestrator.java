package com.frameflow.product.application;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.AnalysisSelection.AnalysisRun;
import com.frameflow.product.domain.AnalysisSelection.Finding;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.infrastructure.persistence.AnalysisRepositories;
import com.frameflow.product.infrastructure.persistence.BatchRepositories;
import com.frameflow.product.infrastructure.persistence.ProjectRepositories;
import com.frameflow.product.infrastructure.worker.WorkerRunner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4 batch engine: bounded sequential orchestration, progress, partial failure
 * reconciliation and rerun. Capacity is bounded by the batch capacity limit; no
 * unbounded in-memory accumulation of candidates occurs.
 */
@Service
public class BatchOrchestrator {

    private final BatchRepositories batchRepos;
    private final ProjectRepositories projectRepos;
    private final AnalysisRepositories analysisRepos;
    private final AnalysisService analysisService;
    private final WorkerRunner worker;

    public BatchOrchestrator(BatchRepositories batchRepos, ProjectRepositories projectRepos,
                             AnalysisRepositories analysisRepos, AnalysisService analysisService,
                             WorkerRunner worker) {
        this.batchRepos = batchRepos;
        this.projectRepos = projectRepos;
        this.analysisRepos = analysisRepos;
        this.analysisService = analysisService;
        this.worker = worker;
    }

    public record BatchProgress(long batchId, Map<String, Long> candidateStatuses,
                                long total, long failed, long analyzed) {
    }

    public BatchProgress progress(long teamId, long batchId) {
        batchRepos.require(teamId, batchId);
        Map<String, Long> counts = new LinkedHashMap<>();
        List<Candidate> candidates = batchRepos.listCandidates(teamId, batchId);
        for (Candidate c : candidates) {
            counts.merge(c.getStatus() == null ? "UPLOADING" : c.getStatus(), 1L, Long::sum);
        }
        long failed = counts.getOrDefault("ANALYSIS_ERROR", 0L) + counts.getOrDefault("UPLOAD_FAILED", 0L)
                + counts.getOrDefault("INVALID", 0L);
        long analyzed = counts.getOrDefault("ANALYZED", 0L);
        return new BatchProgress(batchId, counts, candidates.size(), failed, analyzed);
    }

    /** Process every stored/analyzed-ready candidate through the worker, sequentially. */
    @Transactional
    public BatchProgress processBatch(long teamId, long batchId, long userId) {
        Batch batch = batchRepos.require(teamId, batchId);
        if (batch.getProfileVersionId() == null) {
            throw ApiException.conflict("BATCH_NO_PROFILE", "batch requires a published profile");
        }
        batchRepos.updateStatusAndTimestamps(batchId, "PROCESSING", true, false);
        List<Candidate> candidates = batchRepos.listCandidates(teamId, batchId);
        long failures = 0;
        long analyzed = 0;
        for (Candidate c : candidates) {
            String status = c.getStatus();
            if (!"STORED".equals(status) && !"READY_FOR_ANALYSIS".equals(status)
                    && !"ANALYZED".equals(status) && !"ANALYSIS_ERROR".equals(status)) {
                continue;
            }
            try {
                processOne(teamId, batchId, c.getId(), batch.getProfileVersionId(), userId);
                analyzed++;
            } catch (Exception ex) {
                failures++;
                batchRepos.updateCandidateStatus(c.getId(), "ANALYSIS_ERROR");
            }
        }
        String batchStatus = failures == 0 ? "REVIEW_READY" : "PARTIAL_FAILURE";
        batchRepos.updateStatusAndTimestamps(batchId, batchStatus, false, failures == 0);
        return progress(teamId, batchId);
    }

    public void processOne(long teamId, long batchId, long candidateId, long profileVersionId, long userId) {
        Candidate candidate = batchRepos.requireCandidate(teamId, candidateId);
        long cv = batchRepos.nextCandidateVersion(candidate.getId()) - 1;
        if (cv <= 0) {
            throw ApiException.conflict("CANDIDATE_NOT_STORED", "candidate has no stored version");
        }
        var candidateVersion = batchRepos.requireVersion(candidate.getId(), (int) cv);
        long runId = analysisRepos.createRun(teamId, batchId, candidateVersion.getId(), userId);
        analysisRepos.setRunStarted(runId, "cmd_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        batchRepos.updateCandidateStatus(candidate.getId(), "ANALYZING");

        WorkerRunner.WorkerOutcome outcome = worker.run(teamId, candidateId, profileVersionId, cv);
        analysisService.ingestResult(teamId, runId,
                analysisRepos.commandOf(runId) != null ? analysisRepos.commandOf(runId) : "cmd_pending",
                outcome.status(), outcome.decisionJson(), outcome.qualityVectorJson(), outcome.findings());
        // ingestResultWithCommand completes the run and sets candidate status by outcome
    }

    @Transactional
    public long rerunCandidate(long teamId, long batchId, long candidateId, long userId) {
        Batch batch = batchRepos.require(teamId, batchId);
        Candidate candidate = batchRepos.requireCandidate(teamId, candidateId);
        long cv = batchRepos.nextCandidateVersion(candidate.getId()) - 1;
        if (cv <= 0) {
            throw ApiException.conflict("CANDIDATE_NOT_STORED", "candidate has no stored version");
        }
        var candidateVersion = batchRepos.requireVersion(candidate.getId(), (int) cv);
        long runId = analysisRepos.createRun(teamId, batchId, candidateVersion.getId(), userId);
        String command = "cmd_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        analysisRepos.setRunStarted(runId, command);
        batchRepos.updateCandidateStatus(candidate.getId(), "ANALYZING");
        try {
            WorkerRunner.WorkerOutcome outcome = worker.run(teamId, candidateId, batch.getProfileVersionId(), cv);
            analysisService.ingestResult(teamId, runId, command,
                    outcome.status(), outcome.decisionJson(), outcome.qualityVectorJson(), outcome.findings());
        } catch (Exception workerFailure) {
            // worker unavailable/failure is a recoverable partial failure: record the analysis
            // error as a fresh FAILED run so rerun always yields a new run id (FF-BAT-001 rerun)
            analysisService.ingestResult(teamId, runId, command, "FAILED", "{}", "{}", List.of());
        }
        return runId;
    }
}
