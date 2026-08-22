package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

/** ranking_snapshots 行对象。 */
public class RankingSnapshotRow {

    private Long id;
    private Long batchId;
    private Long profileVersionId;
    private String algorithmVersion;
    private Integer hammingThreshold;
    private Long createdBy;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getProfileVersionId() { return profileVersionId; }
    public void setProfileVersionId(Long profileVersionId) { this.profileVersionId = profileVersionId; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public Integer getHammingThreshold() { return hammingThreshold; }
    public void setHammingThreshold(Integer hammingThreshold) { this.hammingThreshold = hammingThreshold; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
