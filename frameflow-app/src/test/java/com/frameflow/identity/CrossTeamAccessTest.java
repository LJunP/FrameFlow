package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-05 跨团队资源访问返回 404（防枚举） */
class CrossTeamAccessTest extends IdentityIntegrationTestBase {

    @Test
    void test05_crossTeamAccessReturns404() {
        String ownerEmail = nextEmail();
        register(ownerEmail, "passw0rd!");
        Map ownerPair = login(ownerEmail, "passw0rd!");
        String ownerToken = accessTokenOf(ownerPair);

        String outsiderEmail = nextEmail();
        register(outsiderEmail, "passw0rd!");
        Map outsiderPair = login(outsiderEmail, "passw0rd!");
        String outsiderToken = accessTokenOf(outsiderPair);

        Map team = json(postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(ownerToken, UUID.randomUUID().toString())).getBody());
        long teamId = ((Number) team.get("id")).longValue();

        // 非成员读团队 → 404 RESOURCE_NOT_FOUND
        ResponseEntity<String> getTeam = getJson("/api/v1/teams/" + teamId, tokenHeaders(outsiderToken));
        assertThat(getTeam.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(getTeam.getBody()).get("code")).isEqualTo("RESOURCE_NOT_FOUND");

        // 非成员读成员列表 → 404
        ResponseEntity<String> members = getJson("/api/v1/teams/" + teamId + "/members", tokenHeaders(outsiderToken));
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 非成员添加成员 → 404（不是 403，隐藏团队存在性）
        String targetEmail = nextEmail();
        register(targetEmail, "passw0rd!");
        ResponseEntity<String> add = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", targetEmail, "role", "REVIEWER"),
                headersWithKey(outsiderToken, UUID.randomUUID().toString()));
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 非成员 GET /teams 不会看到该团队
        ResponseEntity<String> list = getJson("/api/v1/teams", tokenHeaders(outsiderToken));
        List<Map<String, Object>> items = jsonList(json(list.getBody()).get("items"));
        assertThat(items).isEmpty();

        // 404 requestId 一致
        assertThat(json(getTeam.getBody()).get("requestId"))
                .isEqualTo(getTeam.getHeaders().getFirst("X-Request-Id"));
    }

    private org.springframework.http.HttpHeaders headersWithKey(String token, String key) {
        org.springframework.http.HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}
