package com.frameflow.identity.application;

import com.frameflow.identity.config.JwtProperties;
import com.frameflow.identity.domain.RefreshTokenSession;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.infrastructure.persistence.RefreshTokenSessionMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens: 32 random bytes -> Base64URL no padding; only the
 * SHA-256 hex hash is stored. Rotation swaps one ACTIVE record for a new one;
 * replay of a ROTATED/REVOKED token revokes the whole family (token-contract.md §3).
 */
@Service
@ConditionalOnWebApplication
public class RefreshTokenService {

    private final RefreshTokenSessionMapper sessionMapper;
    private final JwtProperties props;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenSessionMapper sessionMapper, JwtProperties props, Clock clock) {
        this.sessionMapper = sessionMapper;
        this.props = props;
        this.clock = clock;
    }

    public record IssuedToken(String refreshToken, UUID familyId, long sessionId) {
    }

    public record RefreshedToken(String refreshToken, long userId) {
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public IssuedToken issue(long userId) {
        UUID familyId = UUID.randomUUID();
        String token = generateToken();
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setTokenHash(hash(token));
        session.setStatus("ACTIVE");
        session.setExpiresAt(OffsetDateTime.now(clock).plus(props.getRefreshTokenTtl()));
        sessionMapper.insert(session);
        return new IssuedToken(token, familyId, session.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = ApiException.class)
    public RefreshedToken refresh(String rawToken) {
        String hash = hash(rawToken);
        RefreshTokenSession session = sessionMapper.findByHashForUpdate(hash);
        if (session == null) {
            throw ApiException.unauthenticated("Refresh Token 无效");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!"ACTIVE".equals(session.getStatus())) {
            sessionMapper.revokeActiveByFamily(session.getFamilyId());
            throw ApiException.unauthenticated("Refresh Token 已被轮换或撤销（检测到重放），已撤销该 token family");
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            throw ApiException.tokenExpired("Refresh Token 已过期");
        }
        String newToken = generateToken();
        RefreshTokenSession next = new RefreshTokenSession();
        next.setUserId(session.getUserId());
        next.setFamilyId(session.getFamilyId());
        next.setTokenHash(hash(newToken));
        next.setStatus("ACTIVE");
        next.setExpiresAt(now.plus(props.getRefreshTokenTtl()));
        sessionMapper.insert(next);
        sessionMapper.markRotated(session.getId(), next.getId());
        return new RefreshedToken(newToken, session.getUserId());
    }

    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = ApiException.class)
    public void logout(String rawToken) {
        String hash = hash(rawToken);
        RefreshTokenSession session = sessionMapper.findByHashForUpdate(hash);
        if (session == null) {
            throw ApiException.unauthenticated("Refresh Token 无效");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!"ACTIVE".equals(session.getStatus())) {
            sessionMapper.revokeActiveByFamily(session.getFamilyId());
            throw ApiException.unauthenticated("Refresh Token 已被轮换或撤销（检测到重放），已撤销该 token family");
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            throw ApiException.tokenExpired("Refresh Token 已过期");
        }
        sessionMapper.revokeActiveByFamily(session.getFamilyId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        return RequestFingerprint.sha256Hex(rawToken);
    }
}
