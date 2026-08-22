package com.frameflow.learning.identity.repo;

/** team_members 表行对象（复合主键 team_id + user_id，见 V1 迁移注释）。 */
public class MemberRow {

    private Long teamId;
    private Long userId;
    private String role;

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
