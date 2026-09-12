package com.frameflow.learning.product.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** F3 批次与上传接口 DTO。 */
public final class BatchDtos {

    private BatchDtos() {
    }

    public record CreateBatchRequest(
            @NotNull Long projectId,
            @NotNull Long profileId,
            /** 不传 = 用该 profile 的最新版本。 */
            Integer profileVersionNo,
            @NotNull @Min(1) @Max(300) Integer capacity) {
    }

    public record BatchResponse(Long id, Long projectId, Long profileVersionId,
                                Integer profileVersionNo, Long briefId,
                                String status, int capacity,
                                Map<String, Integer> candidateCounts) {
    }

    public record RegisterCandidateRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 128) String contentType,
            @NotNull @Positive long sizeBytes,
            /** 仅支持单文件直传的客户端传 true；服务端在落库前拒绝分片需求。 */
            Boolean simpleOnly) {
    }

    /**
     * 上传指令：SIMPLE 模式直接 PUT uploadUrl；
     * MULTIPART 模式先拿 uploadId，再按需取分片 URL，最后带 ETag 列表确认。
     */
    public record RegisterCandidateResponse(Long candidateId, String mode,
                                            String uploadUrl, String uploadId,
                                            long partSizeBytes, String expiresAt) {
    }

    public record UploadPartsRequest(@NotEmpty List<Integer> partNumbers) {
    }

    public record UploadPartsResponse(Map<Integer, String> partUrls) {
    }

    public record PartResult(@NotNull Integer partNumber, @NotBlank String etag) {
    }

    public record CompleteUploadRequest(List<PartResult> parts) {
    }

    public record CompleteUploadResponse(Long candidateId, String status, String probeError) {
    }

    public record CandidateResponse(Long id, Long batchId, String status, String fileName,
                                    String contentType, long sizeBytes, String objectKey,
                                    String uploadMode, String etag, String probeError) {
    }

    /** 对账报告：数字是各分类数量，keys 是问题对象采样（最多 20 个）。 */
    public record ReconcileResponse(Long batchId, String checkedAt,
                                    int awaitingUpload, int uploadedNotCompleted,
                                    int missingObjects, int orphanObjects,
                                    int invalidatedStalePending,
                                    List<String> missingObjectKeys,
                                    List<String> orphanObjectKeys) {
    }
}
