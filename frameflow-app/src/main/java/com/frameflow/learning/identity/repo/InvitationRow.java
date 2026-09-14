package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

/**
 * team_invitations 表行对象。与 UserRow 同理用传统 POJO
 * （MyBatis 自动映射基于"无参构造 + setter"）。
 */
public class InvitationRow {

    private Long id;
    private Long teamId;
    private String email;
    private String role;
    /** 令牌的 SHA-256（V8 起不再存明文；明文只在创建响应里出现一次）。 */
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
    private Long invitedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
}
