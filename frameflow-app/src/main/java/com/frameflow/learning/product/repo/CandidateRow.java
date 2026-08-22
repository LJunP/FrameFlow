package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

/** candidates 行对象（状态机见 V3 迁移注释）。 */
public class CandidateRow {

    private Long id;
    private Long batchId;
    private String status;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String objectKey;
    private String uploadMode;
    private String s3UploadId;
    private String etag;
    private Long durationMs;
    private Integer width;
    private Integer height;
    private Double fps;
    private String probeError;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime uploadedAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getUploadMode() { return uploadMode; }
    public void setUploadMode(String uploadMode) { this.uploadMode = uploadMode; }
    public String getS3UploadId() { return s3UploadId; }
    public void setS3UploadId(String s3UploadId) { this.s3UploadId = s3UploadId; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Double getFps() { return fps; }
    public void setFps(Double fps) { this.fps = fps; }
    public String getProbeError() { return probeError; }
    public void setProbeError(String probeError) { this.probeError = probeError; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
