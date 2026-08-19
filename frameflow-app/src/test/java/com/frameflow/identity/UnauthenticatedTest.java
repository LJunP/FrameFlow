package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-03 未认证请求返回 401 */
class UnauthenticatedTest extends IdentityIntegrationTestBase {

    @Test
    void test03_unauthenticatedReturns401() {
        ResponseEntity<String> me = getJson("/api/v1/auth/me", new HttpHeaders());
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertCode(me, "AUTH_REQUIRED");

        ResponseEntity<String> teams = getJson("/api/v1/teams", new HttpHeaders());
        assertThat(teams.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertCode(teams, "AUTH_REQUIRED");

        ResponseEntity<String> getTeam = getJson("/api/v1/teams/1", new HttpHeaders());
        assertThat(getTeam.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertCode(getTeam, "AUTH_REQUIRED");

        ResponseEntity<String> members = getJson("/api/v1/teams/1/members", new HttpHeaders());
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 无效 Token 同样 401 AUTH_REQUIRED
        HttpHeaders garbage = new HttpHeaders();
        garbage.setBearerAuth("not-a-real-token");
        ResponseEntity<String> invalid = getJson("/api/v1/auth/me", garbage);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertCode(invalid, "AUTH_REQUIRED");

        // 错误响应 X-Request-Id 存在且 body requestId 与 header 一致
        assertThat(me.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(json(me.getBody()).get("requestId")).isEqualTo(me.getHeaders().getFirst("X-Request-Id"));
    }

    private void assertCode(ResponseEntity<String> response, String code) {
        assertThat(json(response.getBody()).get("code")).isEqualTo(code);
    }
}
