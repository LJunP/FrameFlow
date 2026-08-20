package com.frameflow.product.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.AnalysisSelection.AnalysisRun;
import com.frameflow.product.domain.AnalysisSelection.Finding;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.infrastructure.persistence.AnalysisRepositories;
import com.frameflow.product.infrastructure.persistence.BatchRepositories;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

    private final AnalysisRepositories repos;
    private final BatchRepositories batchRepos;
    private final WorkerDispatcher dispatcher;

    public AnalysisService(AnalysisRepositories repos, BatchRepositories batchRepos,
                           WorkerDispatcher dispatcher) {
        this.repos = repos;
        this.batchRepos = batchRepos;
        this.dispatcher = dispatcher;
    }

    public record StartAnalysisCmd(long teamId, long batchId, long candidateId, long userId) {
    }

    @Transactional
    public long startAnalysis(StartAnalysisCmd cmd) {
        Batch batch = batchRepos.require(cmd.teamId(), cmd.batchId());
        Candidate candidate = batchRepos.requireCandidate(cmd.teamId(), cmd.candidateId());
        if (batch.getProfileVersionId() == null) {
            throw ApiException.conflict("BATCH_NO_PROFILE", "batch requires a published quality profile version");
        }
        long cv = batchRepos.nextCandidateVersion(candidate.getId()) - 1;
        if (cv <= 0) {
            throw ApiException.conflict("CANDIDATE_NOT_STORED", "candidate has no stored version");
        }
        var candidateVersion = batchRepos.requireVersion(candidate.getId(), (int) cv);
        long runId = repos.createRun(cmd.teamId(), cmd.batchId(), candidateVersion.getId(), cmd.userId());
        dispatcher.enqueue(runId);
        String command = dispatcher.commandFor(runId);
        repos.setRunStarted(runId, command == null ? "cmd_pending" : command);
        batchRepos.updateCandidateStatus(candidate.getId(), "ANALYZING");
        return runId;
    }

    public String commandForRun(long runId) {
        return dispatcher.commandFor(runId);
    }

    public List<AnalysisRun> listRuns(long teamId, long batchId) {
        return repos.listRunsByBatch(teamId, batchId);
    }

    public AnalysisRun getRun(long teamId, long id) {
        return repos.requireRun(teamId, id);
    }

    public AnalysisRun getRun(long id) {
        return repos.requireRunAnyTeam(id);
    }

    @Transactional
    public void cancelRun(long teamId, long id) {
        AnalysisRun run = repos.requireRun(teamId, id);
        if ("COMPLETED".equals(run.getStatus()) || "COMPLETED_WITH_FINDINGS".equals(run.getStatus())) {
            throw ApiException.conflict("RUN_ALREADY_COMPLETED", "cannot cancel a completed run");
        }
        repos.setRunStatus(id, "CANCELLED");
    }

    @Transactional
    public void ingestResult(long teamId, long runId, String commandId, String status,
                             String decisionJson, String qualityVectorJson, List<Finding> findings) {
        AnalysisRun run = repos.requireRun(teamId, runId);
        if (run.getCommandId() != null && !run.getCommandId().equals(commandId)) {
            throw ApiException.conflict("COMMAND_MISMATCH", "result command does not match run command");
        }
        if ("COMPLETED".equals(run.getStatus()) || "COMPLETED_WITH_FINDINGS".equals(run.getStatus())) {
            return;
        }
        for (Finding f : findings) {
            f.setAnalysisRunId(runId);
            repos.insertFinding(f, run.getCandidateVersionId());
        }
        repos.completeRun(runId, status, ProjectService.sha256(commandId + decisionJson),
                decisionJson, qualityVectorJson);
        Long candidateId = repos.candidateIdOfRun(runId);
        if (candidateId != null) {
            if ("FAILED".equals(status)) {
                batchRepos.updateCandidateStatus(candidateId, "ANALYSIS_ERROR");
            } else {
                batchRepos.updateCandidateStatus(candidateId, "ANALYZED");
            }
        }
    }

    public List<Finding> listFindingsForCandidate(long teamId, long candidateId) {
        batchRepos.requireCandidate(teamId, candidateId);
        return repos.listFindingsByCandidate(candidateId);
    }

    public List<Finding> listFindingsForRun(long teamId, long runId) {
        repos.requireRun(teamId, runId);
        return repos.listFindingsByRun(runId);
    }
}