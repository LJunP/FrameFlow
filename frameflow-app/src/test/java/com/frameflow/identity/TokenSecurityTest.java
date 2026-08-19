package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-06 密码与 Token 安全规则（BCrypt；Refresh 仅存哈希） */
class TokenSecurityTest extends IdentityIntegrationTestBase {

    @Test
    void test06_passwordAndTokenSecurityRules() {
        String email = nextEmail();
        String password = "secure-password-123";
        register(email, password);
        Map pair = login(email, password);
        String accessToken = accessTokenOf(pair);
        String refreshToken = refreshTokenOf(pair);

        // 数据库中 password_hash 是 BCrypt，不是明文
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo(password);
        assertThat(hash).doesNotContain(password);

        // refresh_token_sessions 只保存 64 位小写十六进制哈希；明文不出现在数据库
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token_sessions WHERE token_hash = ?", Integer.class,
                sha256hex(refreshToken));
        assertThat(rows).isEqualTo(1);
        Integer plaintextRows = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token_sessions WHERE token_hash = ?", Integer.class, refreshToken);
        assertThat(plaintextRows).isZero();
        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_token_sessions LIMIT 1", String.class);
        assertThat(stored).matches("[0-9a-f]{64}");

        // Access Token：RS256 + 非空 kid + 契约 claims；Refresh Token 是不透明串（非 JWT）
        String headerPayload = accessToken.split("\\.")[0];
        String headerJson = new String(java.util.Base64.getUrlDecoder().decode(headerPayload));
        assertThat(headerJson).contains("\"alg\":\"RS256\"").contains("\"kid\":\"");
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payloadJson).contains("\"iss\":\"frameflow\"")
                .contains("\"aud\":\"frameflow-api\"")
                .contains("\"jti\":\"");
        long refreshParts = refreshToken.chars().filter(c -> c == '.').count();
        assertThat(refreshParts).isZero();

        // /me 与注册响应不泄露密码
        ResponseEntity<String> me = getJson("/api/v1/auth/me", tokenHeaders(accessToken));
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).doesNotContain(password);
    }

    private static String sha256hex(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}