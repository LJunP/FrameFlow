package com.frameflow.product.web.dto;

import java.util.List;

/** Product API request/response DTOs (records, framework-free shape). */
public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProjectRequest(String name, String description) {
    }

    public record CreateProfileRequest(String name, String templateType) {
    }

    public record CreateProfileVersionRequest(String payload) {
    }

    public record CreateBatchRequest(String name, String promptText, Long profileVersionId, Long briefId) {
    }

    public record AddCandidatesRequest(List<String> keys, List<String> mediaTypes) {
    }

    public record CompleteUploadRequest(Long sessionId, Long sizeBytes, String contentDigest) {
    }

    public record StartAnalysisRequest(Long candidateId) {
    }

    public record IngestResultRequest(String commandId, String status, String decision, String qualityVector,
                                      List<FindingDto> findings) {
    }

    public record FindingDto(String ruleId, Integer ruleVersion, String detectorId, String detectorVersion,
                             String dimension, String findingType, String verdict, String severity,
                             Double confidence, String automationAction, Integer startMs, Integer endMs,
                             String summary, String evidence, String origin) {
    }

    public record ClusterRequest(Double threshold) {
    }

    public record CreateSelectionRequest(String name, Integer topK) {
    }
}
