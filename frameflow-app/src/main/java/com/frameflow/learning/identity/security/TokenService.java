package com.frameflow.learning.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;

/**
 * 令牌签发：access token（JWT/RS256，短时效）+ refresh token（不透明随机串）。
 * 两种令牌的职责切分是 F1 最重要的安全设计之一，见各方法注释。
 */
@Component
public class TokenService {

    private final RsaKeyProvider keyProvider;
    private final SecurityProperties props;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(RsaKeyProvider keyProvider, SecurityProperties props, Clock clock) {
        this.keyProvider = keyProvider;
        this.props = props;
        this.clock = clock;
    }

    /**
     * 签发 access token。
     *
     * ★ 核心：JWT 的取舍——它"自包含"（验签即可信，不用查库），代价是
     * "签出去就收不回"（有效期内无法单方面作废，除非换密钥）。
     * 所以设计了三层止损：
     * 1) 有效期只有 15 分钟（盗取窗口小）；
     * 2) 敏感操作不依赖它（登出/换 token 走可吊销的 refresh token）；
     * 3) claims 只放最小必要信息（userId/email/teamId/role），不放密码等。
     * 改坏后果：若把有效期调成 30 天，等于放弃了"可吊销性"这整条防线。
     */
    public String issueAccessToken(long userId, String email, long teamId, String role) {
        Instant now = Instant.now(clock);
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))       // sub：标准声明，放 userId
                    .jwtID(UUID.randomUUID().toString())   // jti：唯一 id，审计/将来做黑名单用
                    .claim("email", email)
                    .claim("tid", teamId)
                    .claim("role", role)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(props.accessTokenTtl())))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(keyProvider.rsaKey().getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(keyProvider.rsaKey().toRSAPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("签发 access token 失败", e);
        }
    }

    /**
     * 签发 refresh token：32 字节密码学随机数，Base64Url 编码。
     *
     * ★ 核心：为什么 refresh token 不用 JWT？——它需要"服务端可吊销"。
     * JWT 的天然缺陷是签发后无法收回；而 refresh token 是不透明随机串，
     * 服务端存它的哈希，吊销 = 数据库标记一行，立刻生效。
     * "无状态(JWT)换性能，有状态(不透明)换控制力"，这里选控制力。
     */
    public String issueRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** refresh token 的数据库存储形态：SHA-256 哈希（见 V1 迁移的 ★ 注释）。 */
    public String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public OffsetDateTime refreshTokenExpiry() {
        return OffsetDateTime.ofInstant(Instant.now(clock).plus(props.refreshTokenTtl()), ZoneOffset.UTC);
    }
}
