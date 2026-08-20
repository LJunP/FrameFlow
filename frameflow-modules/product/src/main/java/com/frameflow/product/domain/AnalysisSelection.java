package com.frameflow.product.domain;

import java.time.OffsetDateTime;

/** Aggregates for analysis, findings, ranking, selection and review. */
public final class AnalysisSelection {

    private AnalysisSelection() {
    }

    public static class AnalysisRun {
        private Long id, teamId, batchId, candidateVersionId, createdBy;
        private String status, commandId, resultDigest;
        private String decision;       // JSON
        private String qualityVector;  // JSON
        private OffsetDateTime startedAt, completedAt, createdAt, updatedAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long v) { teamId = v; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long v) { batchId = v; }
        public Long getCandidateVersionId() { return candidateVersionId; }
        public void setCandidateVersionId(Long v) { candidateVersionId = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public String getCommandId() { return commandId; }
        public void setCommandId(String v) { commandId = v; }
        public String getResultDigest() { return resultDigest; }
        public void setResultDigest(String v) { resultDigest = v; }
        public String getDecision() { return decision; }
        public void setDecision(String v) { decision = v; }
        public String getQualityVector() { return qualityVector; }
        public void setQualityVector(String v) { qualityVector = v; }
        public OffsetDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(OffsetDateTime v) { startedAt = v; }
        public OffsetDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(OffsetDateTime v) { completedAt = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    }

    public static class Finding {
        private Long id, analysisRunId, candidateId;
        private String ruleId, detectorId, detectorVersion, dimension, findingType;
        private String verdict, severity, automationAction, summary;
        private Double confidence;
        private Integer startMs, endMs;
        private String evidence, origin;   // JSON
        private OffsetDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getAnalysisRunId() { return analysisRunId; }
        public void setAnalysisRunId(Long v) { analysisRunId = v; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long v) { candidateId = v; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String v) { ruleId = v; }
        public String getDetectorId() { return detectorId; }
        public void setDetectorId(String v) { detectorId = v; }
        public String getDetectorVersion() { return detectorVersion; }
        public void setDetectorVersion(String v) { detectorVersion = v; }
        public String getDimension() { return dimension; }
        public void setDimension(String v) { dimension = v; }
        public String getFindingType() { return findingType; }
        public void setFindingType(String v) { findingType = v; }
        public String getVerdict() { return verdict; }
        public void setVerdict(String v) { verdict = v; }
        public String getSeverity() { return severity; }
        public void setSeverity(String v) { severity = v; }
        public String getAutomationAction() { return automationAction; }
        public void setAutomationAction(String v) { automationAction = v; }
        public String getSummary() { return summary; }
        public void setSummary(String v) { summary = v; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double v) { confidence = v; }
        public Integer getStartMs() { return startMs; }
        public void setStartMs(Integer v) { startMs = v; }
        public Integer getEndMs() { return endMs; }
        public void setEndMs(Integer v) { endMs = v; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String v) { evidence = v; }
        public String getOrigin() { return origin; }
        public void setOrigin(String v) { origin = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    }

    public static class RankingSnapshot {
        private Long id, batchId, createdBy;
        private Integer snapshotVersion;
        private String algorithmVersion, weights, featureSchemaVersion;
        private OffsetDateTime lockedAt, createdAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long v) { batchId = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public Integer getSnapshotVersion() { return snapshotVersion; }
        public void setSnapshotVersion(Integer v) { snapshotVersion = v; }
        public String getAlgorithmVersion() { return algorithmVersion; }
        public void setAlgorithmVersion(String v) { algorithmVersion = v; }
        public String getWeights() { return weights; }
        public void setWeights(String v) { weights = v; }
        public String getFeatureSchemaVersion() { return featureSchemaVersion; }
        public void setFeatureSchemaVersion(String v) { featureSchemaVersion = v; }
        public OffsetDateTime getLockedAt() { return lockedAt; }
        public void setLockedAt(OffsetDateTime v) { lockedAt = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    }

    public static class RankingEntry {
        private Long rankingSnapshotId, candidateId;
        private Integer rank;
        private Double baseScore, finalScore;
        private String breakdown;   // JSON

        public Long getRankingSnapshotId() { return rankingSnapshotId; }
        public void setRankingSnapshotId(Long v) { rankingSnapshotId = v; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long v) { candidateId = v; }
        public Integer getRank() { return rank; }
        public void setRank(Integer v) { rank = v; }
        public Double getBaseScore() { return baseScore; }
        public void setBaseScore(Double v) { baseScore = v; }
        public Double getFinalScore() { return finalScore; }
        public void setFinalScore(Double v) { finalScore = v; }
        public String getBreakdown() { return breakdown; }
        public void setBreakdown(String v) { breakdown = v; }
    }

    public static class SimilarityCluster {
        private Long id, batchId, representativeCandidateId;
        private String clusterKey, algorithmVersion;
        private Double threshold;
        private OffsetDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long v) { batchId = v; }
        public Long getRepresentativeCandidateId() { return representativeCandidateId; }
        public void setRepresentativeCandidateId(Long v) { representativeCandidateId = v; }
        public String getClusterKey() { return clusterKey; }
        public void setClusterKey(String v) { clusterKey = v; }
        public String getAlgorithmVersion() { return algorithmVersion; }
        public void setAlgorithmVersion(String v) { algorithmVersion = v; }
        public Double getThreshold() { return threshold; }
        public void setThreshold(Double v) { threshold = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    }

    public static class SelectionSet {
        private Long id, batchId, lockedBy, createdBy;
        private String name, status, theme;
        private Integer topK;
        private OffsetDateTime lockedAt, createdAt, updatedAt;

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long v) { batchId = v; }
        public Long getLockedBy() { return lockedBy; }
        public void setLockedBy(Long v) { lockedBy = v; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long v) { createdBy = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v; }
        public String getTheme() { return theme; }
        public void setTheme(String v) { theme = v; }
        public Integer getTopK() { return topK; }
        public void setTopK(Integer v) { topK = v; }
        public OffsetDateTime getLockedAt() { return lockedAt; }
        public void setLockedAt(OffsetDateTime v) { lockedAt = v; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    }
}
