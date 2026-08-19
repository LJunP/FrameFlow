package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-12 Refresh Token 哈希存储、轮换与重放撤销 family */
class RefreshTokenTest extends IdentityIntegrationTestBase {

    @Test
    void test12_refreshRotationReplayRevocation() {
        String email = nextEmail();
        register(email, "passw0rd!");
        Map pair = login(email, "passw0rd!");
        String refresh1 = refreshTokenOf(pair);
        String newAccess1 = accessTokenOf(pair);

        // family 内当前只有一条 ACTIVE
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token_sessions WHERE status = 'ACTIVE' AND user_id = "
                        + "(SELECT id FROM users WHERE email = ?)", Integer.class, email);
        assertThat(active).isEqualTo(1);

        // 刷新 → 200 新 token 对；旧 refresh1 立即失效（轮换）
        ResponseEntity<Map> refreshed = rest.postForEntity(url("/api/v1/auth/refresh"),
                Map.of("refreshToken", refresh1), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map newPair = refreshed.getBody();
        String refresh2 = (String) newPair.get("refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh1);
        assertThat(newPair.get("tokenType")).isEqualTo("Bearer");

        // 旧 refresh1 重放 → 401 并撤销整个 family（包括刚轮换出的 refresh2）
        ResponseEntity<Map> replay = rest.postForEntity(url("/api/v1/auth/refresh"),
                Map.of("refreshToken", refresh1), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody().get("code")).isEqualTo("AUTH_REQUIRED");

        ResponseEntity<Map> afterFamilyRevoked = rest.postForEntity(url("/api/v1/auth/refresh"),
                Map.of("refreshToken", refresh2), Map.class);
        assertThat(afterFamilyRevoked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // family 内所有记录不再 ACTIVE
        Integer activeAfter = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token_sessions WHERE status = 'ACTIVE' AND user_id = "
                        + "(SELECT id FROM users WHERE email = ?)", Integer.class, email);
        assertThat(activeAfter).isZero();

        // 新登录产生新 family；登出撤销该 family；登出后 refresh 不可用
        Map pair2 = login(email, "passw0rd!");
        String refresh3 = refreshTokenOf(pair2);
        String access2 = accessTokenOf(pair2);
        assertThat(access2).isNotEqualTo(newAccess1);

        ResponseEntity<Void> logout = rest.exchange(url("/api/v1/auth/logout"),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(Map.of("refreshToken", refresh3)),
                Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterLogout = rest.postForEntity(url("/api/v1/auth/refresh"),
                Map.of("refreshToken", refresh3), Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 未知 token → 401；错误 body requestId 与 header 一致
        ResponseEntity<Map> unknown = rest.postForEntity(url("/api/v1/auth/refresh"),
                Map.of("refreshToken", "x".repeat(43)), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody().get("requestId")).isEqualTo(unknown.getHeaders().getFirst("X-Request-Id"));
    }
}
