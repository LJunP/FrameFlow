package com.frameflow.learning.identity.repo;

/**
 * "团队成员列表"接口的联表查询结果（team_members JOIN users）。
 * 只暴露可对外返回的字段——不含 password_hash，从查询层就杜绝泄漏路径。
 */
public class TeamMemberView {

    private Long userId;
    private String email;
    private String displayName;
    private String role;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
