package com.frameflow.identity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** refresh_token_sessions row (one row per token generation; family groups rotations). */
public class RefreshTokenSession {
    private Long id;
    private Long userId;
    private UUID familyId;
    private String tokenHash;
    private String status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime rotatedAt;
    private OffsetDateTime revokedAt;
    private Long replacedById;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(OffsetDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public OffsetDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(OffsetDateTime rotatedAt) { this.rotatedAt = rotatedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public Long getReplacedById() { return replacedById; }
    public void setReplacedById(Long replacedById) { this.replacedById = replacedById; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
