package com.frameflow.product.domain;

import java.time.OffsetDateTime;

/** Aggregates for batch / candidate / candidate version / upload session. */
public final class BatchCandidate {

    private BatchCandidate() {
    }

    public static class Batch {
        private Long id, teamId, projectId;
        private String name, status, promptText;
        private Long profileVersionId, briefId;
        private Integer capacityLimit, failureCount;
        private Long createdBy;
        private OffsetDateTime startedAt, completedAt, createdAt, updatedAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long v) { teamId = v; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long v) { projectId = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public String getPromptText() { return promptText; }
        public void setPromptText(String v) { promptText = v; }
        public Long getProfileVersionId() { return profileVersionId; }
        public void setProfileVersionId(Long v) { profileVersionId = v; }
        public Long getBriefId() { return briefId; }
        public void setBriefId(Long v) { briefId = v; }
        public Integer getCapacityLimit() { return capacityLimit; }
        public void setCapacityLimit(Integer v) { capacityLimit = v; }
        public Integer getFailureCount() { return failureCount; }
        public void setFailureCount(Integer v) { failureCount = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public OffsetDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(OffsetDateTime v) { startedAt = v; }
        public OffsetDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(OffsetDateTime v) { completedAt = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    }

    public static class Candidate {
        private Long id, teamId, batchId, parentCandidateId, createdBy;
        private String candidateKey, status, mediaType;
        private Long sizeBytes;
        private OffsetDateTime createdAt, updatedAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long v) { teamId = v; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long v) { batchId = v; }
        public Long getParentCandidateId() { return parentCandidateId; }
        public void setParentCandidateId(Long v) { parentCandidateId = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public String getCandidateKey() { return candidateKey; }
        public void setCandidateKey(String v) { candidateKey = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String v) { mediaType = v; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long v) { sizeBytes = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    }

    public static class CandidateVersion {
        private Long id, candidateId;
        private Integer version;
        private String objectRef, contentDigest, mediaType;
        private Long sizeBytes;
        private OffsetDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long v) { candidateId = v; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer v) { version = v; }
        public String getObjectRef() { return objectRef; }
        public void setObjectRef(String v) { objectRef = v; }
        public String getContentDigest() { return contentDigest; }
        public void setContentDigest(String v) { contentDigest = v; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String v) { mediaType = v; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long v) { sizeBytes = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    }

    public static class UploadSession {
        private Long id, candidateId, teamId, createdBy;
        private String storageKey, status;
        private Long expectedSize;
        private String expectedDigest;
        private OffsetDateTime createdAt, updatedAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long v) { candidateId = v; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long v) { teamId = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public String getStorageKey() { return storageKey; }
        public void setStorageKey(String v) { storageKey = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public Long getExpectedSize() { return expectedSize; }
        public void setExpectedSize(Long v) { expectedSize = v; }
        public String getExpectedDigest() { return expectedDigest; }
        public void setExpectedDigest(String v) { expectedDigest = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    }
}
