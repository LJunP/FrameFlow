package com.frameflow.identity.domain;

import java.time.OffsetDateTime;

/** team_members JOIN teams projection (a user's active team membership). */
public class MembershipView {
    private Long teamId;
    private String name;
    private String role;
    private String status;
    private OffsetDateTime createdAt;

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
