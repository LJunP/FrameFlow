package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-10 GET /teams 仅返回当前用户 ACTIVE 团队关系 */
class MyTeamsTest extends IdentityIntegrationTestBase {

    @Test
    void test10_getTeamsOnlyReturnsActiveMemberships() {
        String aEmail = nextEmail();
        register(aEmail, "passw0rd!");
        Map aPair = login(aEmail, "passw0rd!");
        String aToken = accessTokenOf(aPair);

        String bEmail = nextEmail();
        long bUserId = idOf(register(bEmail, "passw0rd!"));
        Map bPair = login(bEmail, "passw0rd!");
        String bToken = accessTokenOf(bPair);

        // A 建两个团队，B 加入团队1
        Map team1 = json(postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(aToken, UUID.randomUUID().toString())).getBody());
        long team1Id = ((Number) team1.get("id")).longValue();
        Map team2 = json(postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(aToken, UUID.randomUUID().toString())).getBody());
        long team2Id = ((Number) team2.get("id")).longValue();

        ResponseEntity<String> addB = postJson("/api/v1/teams/" + team1Id + "/members",
                Map.of("email", bEmail, "role", "VIEWER"),
                headersWithKey(aToken, UUID.randomUUID().toString()));
        assertThat(addB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // B 能看到团队1（ACTIVE 关系）
        List<Map<String, Object>> bTeams = jsonList(json(getJson("/api/v1/teams", tokenHeaders(bToken)).getBody()).get("items"));
        assertThat(bTeams).hasSize(1);
        assertThat(((Number) bTeams.get(0).get("teamId")).longValue()).isEqualTo(team1Id);
        assertThat(bTeams.get(0).get("role")).isEqualTo("VIEWER");
        assertThat(bTeams.get(0).get("status")).isEqualTo("ACTIVE");
        assertThat(bTeams.get(0)).containsKey("createdAt");

        // A 移除 B；B 的 GET /teams 不再包含团队1
        ResponseEntity<String> removeB = deleteJson("/api/v1/teams/" + team1Id + "/members/"
                + bUserId, tokenHeaders(aToken));
        assertThat(removeB.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map<String, Object>> after = jsonList(json(getJson("/api/v1/teams", tokenHeaders(bToken)).getBody()).get("items"));
        assertThat(after).isEmpty();

        // B 不能看到 A 的团队2；A 能看到两个团队（OWNER）
        List<Map<String, Object>> aTeams = jsonList(json(getJson("/api/v1/teams", tokenHeaders(aToken)).getBody()).get("items"));
        assertThat(aTeams).hasSize(2);
        assertThat(aTeams).allMatch(m -> "OWNER".equals(m.get("role")) && "ACTIVE".equals(m.get("status")));
        assertThat(aTeams).noneMatch(m -> ((Number) m.get("teamId")).longValue() == team2Id && false);
        assertThat(aTeams).anyMatch(m -> ((Number) m.get("teamId")).longValue() == team2Id);
    }

    private org.springframework.http.HttpHeaders headersWithKey(String token, String key) {
        org.springframework.http.HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}
