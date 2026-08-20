package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-04 非 OWNER 管理成员返回 403 */
class PermissionTest extends IdentityIntegrationTestBase {

    @Test
    void test04_nonOwnerManagingMembersReturns403() {
        String ownerEmail = nextEmail();
        Map owner = register(ownerEmail, "passw0rd!");
        Map ownerPair = login(ownerEmail, "passw0rd!");
        String ownerToken = accessTokenOf(ownerPair);

        String producerEmail = nextEmail();
        Map producerUser = register(producerEmail, "passw0rd!");
        long producerId = idOf(producerUser);
        Map producerPair = login(producerEmail, "passw0rd!");
        String producerToken = accessTokenOf(producerPair);

        // owner 建团队并添加 producer 为 OPERATOR
        Map team = json(postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(ownerToken, UUID.randomUUID().toString())).getBody());
        long teamId = ((Number) team.get("id")).longValue();
        ResponseEntity<String> add = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", producerEmail, "role", "OPERATOR"),
                headersWithKey(ownerToken, UUID.randomUUID().toString()));
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // producer 尝试添加成员 → 403 FORBIDDEN
        String thirdEmail = nextEmail();
        register(thirdEmail, "passw0rd!");
        ResponseEntity<String> addAttempt = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", thirdEmail, "role", "REVIEWER"),
                headersWithKey(producerToken, UUID.randomUUID().toString()));
        assertThat(addAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(addAttempt.getBody()).get("code")).isEqualTo("FORBIDDEN");

        // producer 尝试改角色 → 403
        ResponseEntity<String> patchAttempt = patchJson("/api/v1/teams/" + teamId + "/members/" + producerId,
                Map.of("role", "VIEWER"), tokenHeaders(producerToken));
        assertThat(patchAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // producer 尝试移除成员 → 403
        ResponseEntity<String> deleteAttempt = deleteJson("/api/v1/teams/" + teamId + "/members/" + producerId,
                tokenHeaders(producerToken));
        assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 403 响应 requestId 一致
        assertThat(json(addAttempt.getBody()).get("requestId"))
                .isEqualTo(addAttempt.getHeaders().getFirst("X-Request-Id"));
    }

    private HttpHeaders headersWithKey(String token, String key) {
        HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}