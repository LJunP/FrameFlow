package com.frameflow.product.application;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.domain.Project;
import com.frameflow.product.infrastructure.persistence.AnalysisRepositories;
import com.frameflow.product.infrastructure.persistence.BatchRepositories;
import com.frameflow.product.infrastructure.persistence.ProjectRepositories;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private final BatchRepositories repos;
    private final ProjectRepositories projectRepos;
    private final AnalysisRepositories analysisRepos;

    public BatchService(BatchRepositories repos, ProjectRepositories projectRepos,
                        AnalysisRepositories analysisRepos) {
        this.repos = repos;
        this.projectRepos = projectRepos;
        this.analysisRepos = analysisRepos;
    }

    public record CreateBatchCmd(long teamId, long projectId, long userId, String name, String promptText,
                                 Long profileVersionId, Long briefId) {
    }

    @Transactional
    public long createBatch(CreateBatchCmd cmd) {
        projectRepos.require(cmd.teamId(), cmd.projectId());
        if (cmd.profileVersionId() != null) {
            projectRepos.requireVersionForTeam(cmd.teamId(), cmd.profileVersionId());
        }
        return repos.create(cmd.teamId(), cmd.projectId(), cmd.userId(), cmd.name(), cmd.promptText(),
                cmd.profileVersionId(), cmd.briefId());
    }

    public List<Batch> listBatches(long teamId, long projectId) {
        return repos.listByProject(teamId, projectId);
    }

    public Batch getBatch(long teamId, long id) {
        return repos.require(teamId, id);
    }

    public Batch getBatch(long id) {
        return repos.findByIdAnyTeam(id);
    }

    public com.frameflow.product.domain.Project projectOf(long projectId) {
        return projectRepos.findByIdAnyTeam(projectId);
    }

    public Candidate getCandidateByIdAnyTeam(long candidateId) {
        return repos.findCandidateAnyTeam(candidateId);
    }

    public record AddCandidatesCmd(long teamId, long batchId, long userId, List<String> keys,
                                   List<String> mediaTypes) {
    }

    @Transactional
    public List<Long> addCandidates(AddCandidatesCmd cmd) {
        Batch batch = repos.require(cmd.teamId(), cmd.batchId());
        if (!"DRAFT".equals(batch.getStatus()) && !"UPLOADING".equals(batch.getStatus())) {
            throw ApiException.conflict("BATCH_NOT_UPLOADING",
                    "candidates may only be added while batch is DRAFT/UPLOADING");
        }
        long current = repos.countCandidates(cmd.batchId());
        int capacity = batch.getCapacityLimit() == null ? 300 : batch.getCapacityLimit();
        if ((long) cmd.keys().size() + current > capacity) {
            throw ApiException.conflict("BATCH_CAPACITY_EXCEEDED", "batch capacity exceeded");
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < cmd.keys().size(); i++) {
            String mediaType = (cmd.mediaTypes() != null && cmd.mediaTypes().size() > i)
                    ? cmd.mediaTypes().get(i) : "video/mp4";
            ids.add(repos.createCandidate(cmd.teamId(), cmd.batchId(), cmd.userId(), cmd.keys().get(i), mediaType));
        }
        return ids;
    }

    public List<Candidate> listCandidates(long teamId, long batchId) {
        return repos.listCandidates(teamId, batchId);
    }

    public Candidate getCandidate(long teamId, long id) {
        return repos.requireCandidate(teamId, id);
    }

    @Transactional
    public long createUploadSession(long teamId, long candidateId, long userId) {
        Candidate candidate = repos.requireCandidate(teamId, candidateId);
        String storageKey = "teams/" + teamId + "/batches/" + candidate.getBatchId()
                + "/candidates/" + candidateId + "/v" + repos.nextCandidateVersion(candidateId) + "/source.mp4";
        return repos.createUploadSession(candidateId, teamId, userId, storageKey);
    }

    @Transactional
    public Candidate completeUpload(long teamId, long sessionId, long size, String digest) {
        var session = repos.requireUploadSession(teamId, sessionId);
        Candidate candidate = repos.requireCandidate(teamId, session.getCandidateId());
        long version = repos.nextCandidateVersion(session.getCandidateId());
        String finalDigest = (digest == null || digest.isBlank())
                ? ProjectService.sha256("candidate:" + candidate.getId()) : digest;
        repos.insertCandidateVersion(candidate.getId(), (int) version, session.getStorageKey(),
                finalDigest, size, candidate.getMediaType());
        repos.completeUploadSession(session.getId(), size, finalDigest);
        repos.updateCandidateSize(candidate.getId(), size);
        repos.updateCandidateStatus(candidate.getId(), "STORED");
        return repos.requireCandidate(teamId, candidate.getId());
    }

    @Transactional
    public void failUpload(long teamId, long sessionId) {
        var session = repos.requireUploadSession(teamId, sessionId);
        repos.failUploadSession(session.getId());
        repos.updateCandidateStatus(session.getCandidateId(), "UPLOAD_FAILED");
    }

    @Transactional
    public void setBatchReady(long teamId, long batchId, long userId) {
        repos.require(teamId, batchId);
        repos.updateStatus(batchId, "READY");
    }

    @Transactional
    public void setBatchQueued(long teamId, long batchId) {
        repos.require(teamId, batchId);
        repos.updateStatus(batchId, "QUEUED");
    }

    public long startAnalysis(long teamId, long batchId, long candidateId, long userId) {
        Batch batch = repos.require(teamId, batchId);
        Candidate candidate = repos.requireCandidate(teamId, candidateId);
        if (batch.getProfileVersionId() == null) {
            throw ApiException.conflict("BATCH_NO_PROFILE", "batch requires a published quality profile version");
        }
        long cv = repos.nextCandidateVersion(candidate.getId()) - 1;
        if (cv <= 0) {
            throw ApiException.conflict("CANDIDATE_NOT_STORED", "candidate has no stored version");
        }
        var candidateVersion = repos.requireVersion(candidate.getId(), (int) cv);
        long runId = analysisRepos.createRun(teamId, batchId, candidateVersion.getId(), userId);
        repos.updateCandidateStatus(candidate.getId(), "READY_FOR_ANALYSIS");
        return runId;
    }
}
