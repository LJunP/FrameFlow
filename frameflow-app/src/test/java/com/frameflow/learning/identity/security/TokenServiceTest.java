package com.frameflow.learning.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;

/**
 * TokenService 纯单元测试：不起 Spring、不进数据库，直接 new。
 * 验证三件事：签名真的能被对应公钥验过、claims 内容正确、时效生效。
 */
class TokenServiceTest {

    private final RsaKeyProvider keyProvider = new RsaKeyProvider();
    private final SecurityProperties props = new SecurityProperties(Duration.ofMinutes(15), Duration.ofDays(14));
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-22T12:00:30Z"), ZoneOffset.UTC);
    private final TokenService service = new TokenService(keyProvider, props, fixedClock);

    @Test
    void access_token_is_rs256_signed_and_carries_expected_claims() throws Exception {
        String token = service.issueAccessToken(42L, "u@example.com", 7L, "OWNER");

        SignedJWT parsed = SignedJWT.parse(token);

        // 用同一把公钥验签——这正是资源服务器（NimbusJwtDecoder）做的事
        assertThat(parsed.verify(new RSASSAVerifier(keyProvider.rsaKey().toRSAPublicKey()))).isTrue();

        var claims = parsed.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getStringClaim("email")).isEqualTo("u@example.com");
        assertThat(claims.getLongClaim("tid")).isEqualTo(7L);
        assertThat(claims.getStringClaim("role")).isEqualTo("OWNER");
        // 签发于固定时钟的此刻、15 分钟后过期
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(Instant.parse("2026-08-22T12:00:30Z"));
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(Instant.parse("2026-08-22T12:15:30Z"));
    }

    @Test
    void refresh_tokens_are_random_and_hash_is_sha256() {
        String t1 = service.issueRefreshToken();
        String t2 = service.issueRefreshToken();

        // 随机性：两次签发绝不相同（若相同，攻击者可预测令牌）
        assertThat(t1).isNotEqualTo(t2).hasSize(43); // 32 字节 Base64Url 无 padding

        // 同一输入哈希确定、不同输入哈希不同——入库比对依赖这两条性质
        assertThat(service.sha256("abc")).isEqualTo(service.sha256("abc"));
        assertThat(service.sha256("abc")).isNotEqualTo(service.sha256("abd"));
    }
}
