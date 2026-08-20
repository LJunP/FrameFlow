package com.frameflow.product.infrastructure.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.AnalysisSelection.Finding;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.domain.BatchCandidate.CandidateVersion;
import com.frameflow.product.domain.QualityProfileVersion;
import com.frameflow.product.infrastructure.persistence.BatchRepositories;
import com.frameflow.product.infrastructure.persistence.ProjectRepositories;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkerRunner {

    private final BatchRepositories batchRepos;
    private final ProjectRepositories projectRepos;
    private final com.frameflow.product.application.StoragePort storage;
    private final ObjectMapper mapper;

    @Value("${frameflow.worker.python:}")
    private String pythonCommand;

    @Value("${frameflow.worker.cli:}")
    private String cliModule;

    public WorkerRunner(BatchRepositories batchRepos, ProjectRepositories projectRepos,
                        com.frameflow.product.application.StoragePort storage,
                        ObjectMapper mapper) {
        this.batchRepos = batchRepos;
        this.projectRepos = projectRepos;
        this.storage = storage;
        this.mapper = mapper;
    }

    public record WorkerOutcome(String status, String decisionJson, String qualityVectorJson,
                                List<Finding> findings) {
    }

    public WorkerOutcome run(long teamId, long candidateId, long profileVersionId,
                             long candidateVersionId) {
        if (pythonCommand == null || pythonCommand.isBlank() || cliModule == null || cliModule.isBlank()) {
            throw ApiException.conflict("WORKER_NOT_CONFIGURED", "python worker not configured");
        }
        Candidate candidate = batchRepos.requireCandidate(teamId, candidateId);
        CandidateVersion cv = batchRepos.requireVersion(candidateId, (int) candidateVersionId);
        QualityProfileVersion profile = projectRepos.requireVersionById(profileVersionId);
        Path media = storage.localPath(cv.getObjectRef());
        if (!media.toFile().exists()) {
            throw ApiException.notFound("candidate media not found for worker");
        }
        Path profileTmp;
        try {
            profileTmp = java.nio.file.Files.createTempFile("ff-profile-", ".json");
            java.nio.file.Files.writeString(profileTmp, profile.getPayload());
        } catch (Exception ex) {
            throw ApiException.badRequest("profile temp write failed");
        }
        ProcessBuilder pb = new ProcessBuilder(pythonCommand, "-m", cliModule, "analyze",
                media.toString(), profileTmp.toString());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!p.waitFor(180, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw ApiException.badRequest("worker analyze failed: " + out.substring(0, Math.min(out.length(), 300)));
            }
            JsonNode doc = mapper.readTree(out);
            String status = doc.path("status").asText("COMPLETED_WITH_FINDINGS");
            String decision = mapper.writeValueAsString(doc.path("decision"));
            String qv = mapper.writeValueAsString(doc.path("qualityVector"));
            List<Finding> findings = new ArrayList<>();
            for (JsonNode fn : doc.path("findings")) {
                Finding f = new Finding();
                f.setCandidateId(candidateId);
                f.setRuleId(fn.path("ruleId").asText());
                f.setDetectorId(fn.path("detectorId").asText());
                f.setDetectorVersion(fn.path("detectorVersion").asText());
                f.setDimension(fn.path("dimension").asText());
                f.setFindingType(fn.path("findingType").asText());
                f.setVerdict(fn.path("verdict").asText());
                f.setSeverity(fn.path("severity").asText());
                f.setConfidence(fn.path("confidence").asDouble(0.5));
                f.setAutomationAction(fn.path("automationAction").asText("REVIEW"));
                if (fn.hasNonNull("startMs")) f.setStartMs(fn.path("startMs").asInt());
                if (fn.hasNonNull("endMs")) f.setEndMs(fn.path("endMs").asInt());
                f.setSummary(fn.path("summary").asText());
                f.setEvidence(mapper.writeValueAsString(fn.path("evidence")));
                f.setOrigin(mapper.writeValueAsString(fn.path("origin")));
                findings.add(f);
            }
            return new WorkerOutcome(status, decision, qv, findings);
        } catch (Exception ex) {
            throw ApiException.badRequest("worker invocation failed: " + ex.getMessage());
        }
    }
}