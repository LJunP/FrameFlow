package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

/** quality_profiles 行对象（latestVersion 来自子查询，非表列）。 */
public class QualityProfileRow {

    private Long id;
    private Long teamId;
    private String name;
    private String description;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Integer latestVersion;   // 可能为 null（还没有任何版本时）

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getLatestVersion() { return latestVersion; }
    public void setLatestVersion(Integer latestVersion) { this.latestVersion = latestVersion; }
}
