package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

/**
 * users 表行对象。用传统 POJO（getter/setter）而不是 record——
 * MyBatis 的自动映射基于"无参构造 + setter"，record 的构造器映射需要
 * 额外开关和 -parameters 编译参数，这里选最稳的写法。
 */
public class UserRow {

    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String status;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
