package com.frameflow.learning.product.repo;

/** ranking_entries 行对象（breakdown 为 JSON 字符串）。 */
public class RankingEntryRow {

    private Long id;
    private Long snapshotId;
    private Long candidateId;
    private Integer rankNo;
    private Integer clusterId;
    private boolean isRepresentative;
    private Integer score;
    private String breakdown;
    private String excludedReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Integer getClusterId() { return clusterId; }
    public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
    public boolean isRepresentative() { return isRepresentative; }
    public void setRepresentative(boolean representative) { isRepresentative = representative; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getBreakdown() { return breakdown; }
    public void setBreakdown(String breakdown) { this.breakdown = breakdown; }
    public String getExcludedReason() { return excludedReason; }
    public void setExcludedReason(String excludedReason) { this.excludedReason = excludedReason; }
}
