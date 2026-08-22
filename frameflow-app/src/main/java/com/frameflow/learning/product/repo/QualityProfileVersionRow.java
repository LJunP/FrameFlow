package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

/** quality_profile_versions 行对象。specJson 为 JSON 字符串（SELECT 时 ::text 取回）。 */
public class QualityProfileVersionRow {

    private Long id;
    private Long profileId;
    private Integer versionNo;
    private String specJson;
    private Long publishedBy;
    private OffsetDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
