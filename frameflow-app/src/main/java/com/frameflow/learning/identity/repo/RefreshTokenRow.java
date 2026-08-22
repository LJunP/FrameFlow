package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

/** refresh_tokens 表行对象。tokenHash 是 SHA-256 十六进制（CHAR(64)）。 */
public class RefreshTokenRow {

    private Long id;
    private Long userId;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
}
