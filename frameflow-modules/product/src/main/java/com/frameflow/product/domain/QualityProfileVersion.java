package com.frameflow.product.domain;

import java.time.OffsetDateTime;

public class QualityProfileVersion {
    private Long id;
    private Long profileId;
    private Integer version;
    private String status;         // DRAFT | PUBLISHED | RETIRED
    private String payload;        // JSONB (string projection)
    private String payloadDigest;
    private OffsetDateTime publishedAt;
    private Long createdBy;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getPayloadDigest() { return payloadDigest; }
    public void setPayloadDigest(String payloadDigest) { this.payloadDigest = payloadDigest; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
