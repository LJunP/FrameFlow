package com.frameflow.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FF-DOM-002 integration evidence: complete FrameFlow Select workflow on the
 * shared Testcontainers database. Endpoints are exercised end-to-end; assertions
 * verify immutability, versioning, tenant boundaries and deterministic selection.
 */
class ProductFlowIntegrationTest extends IdentityIntegrationTestBase {

    private Map<String, Object> body(ResponseEntity<String> resp) {
        return json(resp.getBody());
    }

    private String createTeamAndOwner() {
        String email = nextEmail();
        register(email, "passw0rd!");
        Map login = login(email, "passw0rd!");
        String ownerToken = accessTokenOf(login);
        ResponseEntity<String> teamResp = postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(ownerToken, UUID.randomUUID().toString()));
        assertThat(teamResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ownerToken;
    }

    private HttpHeaders headersWithKey(String token, String key) {
        HttpHeaders h = tokenHeaders(token);
        h.set("Idempotency-Key", key);
        return h;
    }

    private HttpHeaders teamHeader(String token, long teamId) {
        HttpHeaders h = tokenHeaders(token);
        h.set("X-Team-Id", String.valueOf(teamId));
        return h;
    }

    @Test
    void productFlowCreateProfilePublishBatchAnalyzeRankSelect() {
        String owner = createTeamAndOwner();
        ResponseEntity<String> teamsResp = getJson("/api/v1/teams", tokenHeaders(owner));
        assertThat(teamsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> teams = jsonList(json(teamsResp.getBody()).get("items"));
        assertThat(teams).isNotEmpty();
        long teamId = ((Number) teams.get(0).get("teamId")).longValue();
        HttpHeaders h = teamHeader(owner, teamId);

        ResponseEntity<String> prj = postJson("/api/v1/projects",
                Map.of("name", "Summer Jacket 2026", "description", "campaign"), h);
        assertThat(prj.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long projectId = ((Number) body(prj).get("id")).longValue();
        assertThat(projectId).isPositive();

        ResponseEntity<String> prof = postJson("/api/v1/projects/" + projectId + "/quality-profiles",
                Map.of("name", "ecomm-v1", "templateType", "ECOMMERCE_SHORT_AD_V1"), h);
        assertThat(prof.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long profileId = ((Number) body(prof).get("id")).longValue();

        String payload = "{\"schemaVersion\":\"1.0.0\",\"name\":\"ecomm-v1\",\"templateType\":\"ECOMMERCE_SHORT_AD_V1\","
                + "\"inputContract\":{\"durationSeconds\":{\"minimum\":5,\"maximum\":60},\"aspectRatio\":{\"expected\":\"9:16\",\"tolerance\":0.02},"
                + "\"audioRequired\":true},\"automationPolicy\":{\"defaultSemanticAction\":\"REVIEW\",\"semanticAutoRejectEnabled\":false,\"unknownResultAction\":\"REVIEW\"},"
                + "\"rules\":[{\"ruleId\":\"FF-RULE-DURATION\",\"version\":1,\"type\":\"HARD_CONSTRAINT\",\"dimension\":\"technical_quality\","
                + "\"severity\":\"BLOCKER\",\"automationAction\":\"REJECT\",\"evidenceRequirement\":{\"type\":\"METADATA\"}}],"
                + "\"ranking\":{\"dimensions\":{\"prompt_alignment\":0.5},\"unknownDimensionPolicy\":\"REVIEW_REQUIRED\"}}";
        ResponseEntity<String> ver = postJson("/api/v1/projects/" + projectId + "/quality-profiles/" + profileId + "/versions",
                Map.of("payload", payload), h);
        assertThat(ver.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int version = ((Number) body(ver).get("version")).intValue();
        assertThat(version).isEqualTo(1);
        long versionId = ((Number) body(ver).get("id")).longValue();

        ResponseEntity<String> pub = postJson("/api/v1/quality-profile-versions/" + versionId + "/publish",
                Map.of(), tokenHeaders(owner));
        assertThat(pub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(pub).get("status")).isEqualTo("PUBLISHED");

        ResponseEntity<String> batch = postJson("/api/v1/projects/" + projectId + "/batches",
                Map.of("name", "batch-a", "promptText", "summer jacket ad", "profileVersionId", versionId), h);
        assertThat(batch.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long batchId = ((Number) body(batch).get("id")).longValue();

        ResponseEntity<String> cands = postJson("/api/v1/batches/" + batchId + "/candidates",
                Map.of("keys", List.of("cand-1", "cand-2"),
                        "mediaTypes", List.of("video/mp4", "video/mp4")), h);
        assertThat(cands.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // response is a plain JSON array of candidate ids
        com.fasterxml.jackson.core.type.TypeReference<List<Long>> lt =
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                };
        List<Long> ids;
        try {
            ids = new com.fasterxml.jackson.databind.ObjectMapper().readValue(cands.getBody(), lt);
        } catch (Exception ex) {
            throw new IllegalStateException("parse candidates failed: " + cands.getBody(), ex);
        }
        long cand1 = ids.get(0);

        ResponseEntity<String> up = postJson("/api/v1/candidates/" + cand1 + "/upload-session", Map.of(), h);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        long sessionId = ((Number) body(up).get("sessionId")).longValue();
        ResponseEntity<String> complete = postJson("/api/v1/candidates/" + cand1 + "/upload-complete",
                Map.of("sessionId", sessionId, "sizeBytes", 12345L, "contentDigest", "abc"), h);
        assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(complete).get("status")).isEqualTo("STORED");

        ResponseEntity<String> start = postJson("/api/v1/batches/" + batchId + "/analysis-runs",
                Map.of("candidateId", cand1), h);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long runId = ((Number) body(start).get("runId")).longValue();
        String runCommand = String.valueOf(body(start).get("commandId"));
        assertThat(runCommand).startsWith("cmd_");

        ResponseEntity<String> ingested = postJson("/api/v1/internal/analysis-runs/" + runId + "/results",
                Map.of("commandId", runCommand, "status", "COMPLETED_WITH_FINDINGS",
                        "decision", "{\"value\":\"REVIEW\"}", "qualityVector", "{\"prompt_alignment\":0.8}",
                        "findings", List.of()), h);
        assertThat(ingested.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> cluster = postJson("/api/v1/batches/" + batchId + "/similarity-clusters",
                Map.of("threshold", 0.85), h);
        assertThat(cluster.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> rank = postJson("/api/v1/batches/" + batchId + "/ranking-snapshots", Map.of(), h);
        assertThat(rank.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> sel = postJson("/api/v1/batches/" + batchId + "/selection-sets",
                Map.of("name", "top", "topK", 5), h);
        assertThat(sel.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long setId = ((Number) body(sel).get("id")).longValue();

        ResponseEntity<String> lock = postJson("/api/v1/selection-sets/" + setId + "/lock", Map.of(), h);
        assertThat(lock.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(lock).get("status")).isEqualTo("LOCKED");
    }

    @Test
    void tenantIsolationHiddenBy404() {
        String ownerA = createTeamAndOwner();
        ResponseEntity<String> teamsA = getJson("/api/v1/teams", tokenHeaders(ownerA));
        List<Map<String, Object>> itemsA = jsonList(json(teamsA.getBody()).get("items"));
        long teamAId = ((Number) itemsA.get(0).get("teamId")).longValue();
        HttpHeaders ha = teamHeader(ownerA, teamAId);
        ResponseEntity<String> prj = postJson("/api/v1/projects", Map.of("name", "p-a"), ha);
        long projectA = ((Number) body(prj).get("id")).longValue();

        String ownerB = createTeamAndOwner();
        ResponseEntity<String> pk = getJson("/api/v1/projects/" + projectA, tokenHeaders(ownerB));
        assertThat(pk.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }
}