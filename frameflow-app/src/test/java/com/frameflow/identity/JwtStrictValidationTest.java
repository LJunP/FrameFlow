package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import com.frameflow.identity.support.TestTokenFactory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-11 RS256 alg、kid、iss 与 aud 强校验 */
class JwtStrictValidationTest extends IdentityIntegrationTestBase {

    @Test
    void test11_rs256AlgKidIssAudStrictValidation() {
        String email = nextEmail();
        register(email, "passw0rd!");
        Map pair = login(email, "passw0rd!");
        long userId = idOf(json(getJson("/api/v1/auth/me", tokenHeaders(accessTokenOf(pair))).getBody()));

        // 合法 token 可用
        assertThat(getJson("/api/v1/auth/me", tokenHeaders(accessTokenOf(pair))).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 非 RS256 算法 → 401
        assert401(TestTokenFactory.nonRs256Token(String.valueOf(userId)));

        // 错误 iss → 401
        assert401(TestTokenFactory.rs256Token(String.valueOf(userId), 15,
                b -> b.issuer("evil-issuer"), null));

        // 错误 aud → 401
        assert401(TestTokenFactory.rs256Token(String.valueOf(userId), 15,
                b -> b.audience(java.util.List.of("other-api")), null));

        // 错误 kid → 401
        assert401(TestTokenFactory.rs256Token(String.valueOf(userId), 15, null,
                h -> h.keyId("wrong-kid")));

        // 缺失 kid → 401：header 不含 kid（篡改 header 后签名失效，服务端按无效 Token 拒绝）
        assert401(rs256TokenWithoutKid(String.valueOf(userId)));

        // 过期 token（正确签名，exp 在过去）→ 401 TOKEN_EXPIRED
        // issuedAt/expiresAt 由 claims 定制器显式构造：结构合法（exp > iat）但均已过期
        Instant pastIssued = Instant.now().minusSeconds(3600);
        ResponseEntity<String> expired =
                getJson("/api/v1/auth/me", tokenHeaders(TestTokenFactory.rs256Token(String.valueOf(userId), 15,
                        b -> b.issuedAt(pastIssued).expiresAt(pastIssued.plusSeconds(300)), null)));
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(expired.getBody()).get("code")).isEqualTo("TOKEN_EXPIRED");
    }

    private void assert401(String token) {
        ResponseEntity<String> response = getJson("/api/v1/auth/me", tokenHeaders(token));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(response.getBody()).get("code")).isEqualTo("AUTH_REQUIRED");
    }

    private String rs256TokenWithoutKid(String subject) {
        StringBuilder sb = new StringBuilder();
        String token = com.frameflow.identity.support.TestTokenFactory.rs256Token(subject, 15, null, null);
        // strip the kid claim from the header payload and re-encode
        String[] parts = token.split("\\.");
        String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
        java.util.Map<String, Object> header = new java.util.LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            header = om.readValue(headerJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        header.remove("kid");
        try {
            String newHeader = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(header));
            return newHeader + "." + parts[1] + "." + parts[2];
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}