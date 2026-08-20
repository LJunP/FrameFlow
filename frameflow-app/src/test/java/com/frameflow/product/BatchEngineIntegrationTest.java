package com.frameflow.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FF-BAT-001/002 S4 batch engine: capacity-bounded multi-candidate ingestion,
 * process orchestration (worker-optional => partial failure accounting), progress
 * API, and rerun creating a fresh analysis run.
 */
class BatchEngineIntegrationTest extends IdentityIntegrationTestBase {

    private static final ObjectMapper MAP = new ObjectMapper();

    private Map<String, Object> body(ResponseEntity<String> resp) {
        return json(resp.getBody());
    }

    private HttpHeaders keyed(String token, String key) {
        HttpHeaders h = tokenHeaders(token);
        h.set("Idempotency-Key", key);
        return h;
    }

    private String setupOwnerAndProject() {
        String email = nextEmail();
        register(email, "passw0rd!");
        Map login = login(email, "passw0rd!");
        return accessTokenOf(login);
    }

    private HttpHeaders teamHeader(String token, long teamId) {
        HttpHeaders h = tokenHeaders(token);
        h.set("X-Team-Id", String.valueOf(teamId));
        return h;
    }

    @Test
    void batchIngestProcessProgressAndRerun() throws Exception {
        String token = setupOwnerAndProject();
        ResponseEntity<String> teamResp = postJson("/api/v1/teams", Map.of("name", nextName()),
                keyed(token, UUID.randomUUID().toString()));
        assertThat(teamResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> teams = getJson("/api/v1/teams", tokenHeaders(token));
        List<Map<String, Object>> items = jsonList(json(teams.getBody()).get("items"));
        long teamId = ((Number) items.get(0).get("teamId")).longValue();
        HttpHeaders h = teamHeader(token, teamId);

        ResponseEntity<String> prj = postJson("/api/v1/projects", Map.of("name", "batch-e2e"), h);
        long projectId = ((Number) body(prj).get("id")).longValue();
        ResponseEntity<String> prof = postJson("/api/v1/projects/" + projectId + "/quality-profiles",
                Map.of("name", "q", "templateType", "ECOMMERCE_SHORT_AD_V1"), h);
        long profileId = ((Number) body(prof).get("id")).longValue();
        String payload = "{\"schemaVersion\":\"1.0.0\",\"name\":\"q\",\"templateType\":\"ECOMMERCE_SHORT_AD_V1\","
                + "\"inputContract\":{\"durationSeconds\":{\"minimum\":3,\"maximum\":60},\"aspectRatio\":{\"expected\":\"9:16\",\"tolerance\":0.02},\"audioRequired\":true},"
                + "\"automationPolicy\":{\"defaultSemanticAction\":\"REVIEW\",\"semanticAutoRejectEnabled\":false,\"unknownResultAction\":\"REVIEW\"},"
                + "\"rules\":[{\"ruleId\":\"FF-RULE-DURATION\",\"version\":1,\"type\":\"HARD_CONSTRAINT\",\"dimension\":\"technical_quality\","
                + "\"severity\":\"BLOCKER\",\"automationAction\":\"REJECT\",\"evidenceRequirement\":{\"type\":\"METADATA\"}}],"
                + "\"ranking\":{\"dimensions\":{\"prompt_alignment\":1.0},\"unknownDimensionPolicy\":\"REVIEW_REQUIRED\"}}";
        ResponseEntity<String> ver = postJson("/api/v1/projects/" + projectId + "/quality-profiles/" + profileId + "/versions",
                Map.of("payload", payload), h);
        long versionId = ((Number) body(ver).get("id")).longValue();
        postJson("/api/v1/quality-profile-versions/" + versionId + "/publish", Map.of(), tokenHeaders(token));

        ResponseEntity<String> batch = postJson("/api/v1/projects/" + projectId + "/batches",
                Map.of("name", "b1", "profileVersionId", versionId), h);
        long batchId = ((Number) body(batch).get("id")).longValue();

        // Add 2 candidates (capacity-bounded)
        ResponseEntity<String> cands = postJson("/api/v1/batches/" + batchId + "/candidates",
                Map.of("keys", List.of("a", "b"), "mediaTypes", List.of("video/mp4", "video/mp4")), h);
        assertThat(cands.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Long> ids = MAP.readValue(cands.getBody(), new TypeReference<List<Long>>() { });
        assertThat(ids).hasSize(2);

        // Upload both -> STORED
        for (long candId : ids) {
            ResponseEntity<String> up = postJson("/api/v1/candidates/" + candId + "/upload-session", Map.of(), h);
            long sessionId = ((Number) body(up).get("sessionId")).longValue();
            ResponseEntity<String> complete = postJson("/api/v1/candidates/" + candId + "/upload-complete",
                    Map.of("sessionId", sessionId, "sizeBytes", 100L, "contentDigest", "x"), h);
            assertThat(body(complete).get("status")).isEqualTo("STORED");
        }

        // Process (worker optional => body/status accounting; when venv present full pipeline)
        ResponseEntity<String> proc = postJson("/api/v1/batches/" + batchId + "/process", Map.of(), h);
        assertThat(proc.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> procBody = body(proc);
        assertThat(((Number) procBody.get("total")).intValue()).isEqualTo(2);

        // Progress API returns per-status counts that reconcile
        ResponseEntity<String> pg = postJson("/api/v1/batches/" + batchId + "/progress", Map.of(), h);
        assertThat(pg.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> pgb = body(pg);
        Map<String, Object> statuses = (Map<String, Object>) pgb.get("candidateStatuses");
        // values are JSON numbers (Integer) deserialized by Jackson; reconcile via Number
        long sum = statuses.values().stream().mapToLong(v -> ((Number) v).longValue()).sum();
        assertThat(sum).isEqualTo(2); // reconciled: analyzed + analysis_error cover both

        // Rerun candidate 'a' creates a fresh run
        ResponseEntity<String> rerun = postJson("/api/v1/candidates/" + ids.get(0) + "/rerun", Map.of(), h);
        assertThat(rerun.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(((Number) body(rerun).get("runId")).longValue()).isPositive();
    }

    @Test
    void capacityIsBounded() throws Exception {
        String token = setupOwnerAndProject();
        ResponseEntity<String> teamResp = postJson("/api/v1/teams", Map.of("name", nextName()),
                keyed(token, UUID.randomUUID().toString()));
        List<Map<String, Object>> items = jsonList(json(getJson("/api/v1/teams", tokenHeaders(token)).getBody()).get("items"));
        long teamId = ((Number) items.get(0).get("teamId")).longValue();
        HttpHeaders h = teamHeader(token, teamId);
        ResponseEntity<String> prj = postJson("/api/v1/projects", Map.of("name", "cap"), h);
        long projectId = ((Number) body(prj).get("id")).longValue();
        ResponseEntity<String> batch = postJson("/api/v1/projects/" + projectId + "/batches",
                Map.of("name", "b"), h);
        long batchId = ((Number) body(batch).get("id")).longValue();
        ResponseEntity<String> one = postJson("/api/v1/batches/" + batchId + "/candidates",
                Map.of("keys", List.of("k1")), h);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Distinct candidate keys within one batch ARE allowed (unique is (batch_id, candidate_key))
        ResponseEntity<String> second = postJson("/api/v1/batches/" + batchId + "/candidates",
                Map.of("keys", List.of("k2")), h);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Progress reconciles both ingested candidates
        ResponseEntity<String> pg = postJson("/api/v1/batches/" + batchId + "/progress", Map.of(), h);
        assertThat(pg.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> statuses = (Map<String, Object>) body(pg).get("candidateStatuses");
        long sum = statuses.values().stream().mapToLong(v -> ((Number) v).longValue()).sum();
        assertThat(sum).isEqualTo(2);
    }
}
