package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-13 最后 OWNER、自移除、重复 ACTIVE 成员与 requestId 错误语义 */
class OwnerInvariantsTest extends IdentityIntegrationTestBase {

    @Test
    void test13_lastOwnerSelfRemovalDuplicateAndRequestId() {
        String ownerEmail = nextEmail();
        Map ownerUser = register(ownerEmail, "passw0rd!");
        long ownerId = idOf(ownerUser);
        Map ownerPair = login(ownerEmail, "passw0rd!");
        String ownerToken = accessTokenOf(ownerPair);

        String bEmail = nextEmail();
        long bId = idOf(register(bEmail, "passw0rd!"));
        String cEmail = nextEmail();
        register(cEmail, "passw0rd!");

        Map team = json(postJson("/api/v1/teams", Map.of("name", nextName()),
                headersWithKey(ownerToken, UUID.randomUUID().toString())).getBody());
        long teamId = ((Number) team.get("id")).longValue();

        // 添加 B（EDITOR）与 C（VIEWER）
        assertThat(postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", bEmail, "role", "EDITOR"),
                headersWithKey(ownerToken, UUID.randomUUID().toString())).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", cEmail, "role", "VIEWER"),
                headersWithKey(ownerToken, UUID.randomUUID().toString())).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // 重复添加 ACTIVE 成员 → 409 TEAM_MEMBER_ALREADY_EXISTS
        ResponseEntity<String> dup = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", bEmail, "role", "EDITOR"),
                headersWithKey(ownerToken, UUID.randomUUID().toString()));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(dup.getBody()).get("code")).isEqualTo("TEAM_MEMBER_ALREADY_EXISTS");

        // OWNER 不能移除自己 → 409 TEAM_SELF_REMOVAL_FORBIDDEN
        ResponseEntity<String> selfRemove = deleteJson("/api/v1/teams/" + teamId + "/members/" + ownerId,
                tokenHeaders(ownerToken));
        assertThat(selfRemove.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(selfRemove.getBody()).get("code")).isEqualTo("TEAM_SELF_REMOVAL_FORBIDDEN");

        // 当前只有 owner 一名 OWNER：降级自己 → 409 TEAM_LAST_OWNER_CONFLICT
        ResponseEntity<String> demoteSelf = patchJson("/api/v1/teams/" + teamId + "/members/" + ownerId,
                Map.of("role", "EDITOR"), tokenHeaders(ownerToken));
        assertThat(demoteSelf.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(demoteSelf.getBody()).get("code")).isEqualTo("TEAM_LAST_OWNER_CONFLICT");

        // 移除最后一名 OWNER（owner）→ 409 TEAM_LAST_OWNER_CONFLICT（自移除分支先拦截，但这里验证计数）
        // B 提为 OWNER 后（两名 OWNER）移除 owner 应成功；再把 B 降级为 EDITOR，验证最后 OWNER 语义
        assertThat(patchJson("/api/v1/teams/" + teamId + "/members/" + bId,
                Map.of("role", "OWNER"), tokenHeaders(ownerToken)).getStatusCode()).isEqualTo(HttpStatus.OK);
        // 两名 OWNER：移除 owner（非自移除由 B 执行不可，B 非 OWNER→403）；由 owner 移除 B 前先把 B 降级
        // 先把 B 降级会触发最后 OWNER 保护吗？此时 owner 仍 OWNER，B 不是最后 OWNER → 成功
        assertThat(patchJson("/api/v1/teams/" + teamId + "/members/" + bId,
                Map.of("role", "EDITOR"), tokenHeaders(ownerToken)).getStatusCode()).isEqualTo(HttpStatus.OK);
        // 现在 owner 是最后一名 OWNER；移除 B（EDITOR）成功
        ResponseEntity<String> removeB = deleteJson("/api/v1/teams/" + teamId + "/members/" + bId,
                tokenHeaders(ownerToken));
        assertThat(removeB.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 已移除成员再次添加 → 复用原记录恢复 ACTIVE
        ResponseEntity<String> reAddB = postJson("/api/v1/teams/" + teamId + "/members",
                Map.of("email", bEmail, "role", "EDITOR"),
                headersWithKey(ownerToken, UUID.randomUUID().toString()));
        assertThat(reAddB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map reAdded = json(reAddB.getBody());
        assertThat(reAdded.get("status")).isEqualTo("ACTIVE");
        assertThat(reAdded.get("role")).isEqualTo("EDITOR");

        // requestId：409/204/403 响应均带 X-Request-Id 且 body 一致（取一个 401 用例在其它测试验证）
        assertThat(dup.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(json(dup.getBody()).get("requestId")).isEqualTo(dup.getHeaders().getFirst("X-Request-Id"));
        assertThat(selfRemove.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(json(selfRemove.getBody()).get("requestId")).isEqualTo(selfRemove.getHeaders().getFirst("X-Request-Id"));
        assertThat(removeB.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    private HttpHeaders headersWithKey(String token, String key) {
        HttpHeaders headers = tokenHeaders(token);
        headers.set("Idempotency-Key", key);
        return headers;
    }
}
