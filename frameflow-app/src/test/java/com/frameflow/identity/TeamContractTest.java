package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-02 团队和成员契约测试（含 GET /teams 形状） */
class TeamContractTest extends IdentityIntegrationTestBase {

    @Test
    void test02_teamAndMemberContract() {
        String ownerEmail = nextEmail();
        String memberEmail = nextEmail();
        Map owner = register(ownerEmail, "passw0rd!");
        Map ownerPair = login(ownerEmail, "passw0rd!");
        String ownerToken = accessTokenOf(ownerPair);
        register(memberEmail, "passw0rd!");
        long ownerId = idOf(owner);

        // 创建团队 201 + 自动成为 OWNER；同一事务内写入成员关系
        String teamName = nextName();
        ResponseEntity<String> created = postJson("/api/v1/teams",
                Map.of("name", teamName), headersWithKey(ownerToken, java.util.UUID.randomUUID().toString()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map team = json(created.getBody());
        assertThat(team).containsKeys("id", "name", "createdAt");
        long teamId = ((Number) team.get("id")).longValue();

        // GET /teams 只包含创建者的 OWNER 关系
        ResponseEntity<String> myTeams = getJson("/api/v1/teams", tokenHeaders(ownerToken));
        assertThat(myTeams.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> memberships = jsonList(json(myTeams.getBody()).get("items"));
        assertThat(memberships).anyMatch(m ->
                ((Number) m.get("teamId")).longValue() == teamId
                        && "OWNER".equals(m.get("role")) && "ACTIVE".equals(m.get("status")));

        // GET /teams/{teamId} 200
        ResponseEntity<String> getTeam = getJson("/api/v1/teams/" + teamId, tokenHeaders(ownerToken));
        assertThat(getTeam.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(getTeam.getBody()).get("name")).isEqualTo(teamName);

        // GET members：包含 OWNER 本人
        ResponseEntity<String> members = getJson("/api/v1/teams/" + teamId + "/members", tokenHeaders(ownerToken));
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> memberItems = jsonList(json(members.getBody()).get("items"));
        assertThat(memberItems).anyMatch(m -> ((Number) m.get("userId")).longValue() == ownerId
                && "OWNER".equals(m.get("role")) && "ACTIVE".equals(m.get("status")));

        // 添加已注册成员 201 TeamMember 结构
        ResponseEntity<String> add = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", memberEmail, "role", "EDITOR"),
                headersWithKey(ownerToken, java.util.UUID.randomUUID().toString()));
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map added = json(add.getBody());
        assertThat(added).containsKeys("id", "teamId", "userId", "email", "role", "status");
        assertThat(added.get("role")).isEqualTo("EDITOR");
        assertThat(added.get("status")).isEqualTo("ACTIVE");
        long memberId = ((Number) added.get("userId")).longValue();

        // 修改角色 200
        ResponseEntity<String> patch = patchJson("/api/v1/teams/" + teamId + "/members/" + memberId,
                Map.of("role", "VIEWER"), tokenHeaders(ownerToken));
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(patch.getBody()).get("role")).isEqualTo("VIEWER");

        // 移除成员 204，随后成员列表不再包含
        ResponseEntity<String> remove = deleteJson("/api/v1/teams/" + teamId + "/members/" + memberId, tokenHeaders(ownerToken));
        assertThat(remove.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> after = getJson("/api/v1/teams/" + teamId + "/members", tokenHeaders(ownerToken));
        List<Map<String, Object>> afterItems = jsonList(json(after.getBody()).get("items"));
        assertThat(afterItems).noneMatch(m -> ((Number) m.get("userId")).longValue() == memberId);
    }

    private org.springframework.http.HttpHeaders headersWithKey(String token, String key) {
        org.springframework.http.HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}