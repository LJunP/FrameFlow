package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

/** analysis_runs 行对象。 */
public class AnalysisRunRow {

    private Long id;
    private Long candidateId;
    private Long batchId;
    private String workerVersion;
    private String status;
    private String errorSummary;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getWorkerVersion() { return workerVersion; }
    public void setWorkerVersion(String workerVersion) { this.workerVersion = workerVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
