package com.frameflow.learning.product.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.CandidateRow;
import com.frameflow.learning.product.storage.S3StorageAdapter;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.product.storage.StorageProperties;
import com.frameflow.learning.product.web.BatchDtos.CompleteUploadRequest;
import com.frameflow.learning.product.web.BatchDtos.CompleteUploadResponse;
import com.frameflow.learning.product.web.BatchDtos.PartResult;
import com.frameflow.learning.product.web.BatchDtos.UploadPartsResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 候选上传的完成链路：分片 URL 发放 → 完成（入口校验）→ 状态落位。
 */
@Service
public class UploadService {

    private final BatchService batchService;
    private final CandidateMapper candidates;
    private final StoragePort storage;
    private final StorageProperties storageProps;

    public UploadService(BatchService batchService, CandidateMapper candidates,
                         StoragePort storage, StorageProperties storageProps) {
        this.batchService = batchService;
        this.candidates = candidates;
        this.storage = storage;
        this.storageProps = storageProps;
    }

    /** 分片直传 URL：客户端按需领取（不必一次拿全）。 */
    public UploadPartsResponse uploadParts(long userId, long candidateId, List<Integer> partNumbers) {
        CandidateRow candidate = requireWritableCandidate(userId, candidateId);
        if (!"MULTIPART".equals(candidate.getUploadMode()) || candidate.getS3UploadId() == null) {
            throw new ApiException(ErrorCode.INVALID_UPLOAD_STATE, "该候选不是分片上传会话");
        }
        for (int part : partNumbers) {
            // S3 限制：分片号 1..10000
            if (part < 1 || part > 10000) {
                throw new ApiException(ErrorCode.PARTS_INVALID, "分片号必须在 1..10000 之间");
            }
        }
        Map<Integer, String> urls = new TreeMap<>();
        for (int part : partNumbers) {
            urls.put(part, storage.presignUploadPart(candidate.getObjectKey(),
                    candidate.getS3UploadId(), part, storageProps.presignTtl()));
        }
        return new UploadPartsResponse(urls);
    }

    /**
     * 完成上传：SIMPLE 核对象；MULTIPART 先合并分片；随后统一入口校验。
     */
    public CompleteUploadResponse complete(long userId, long candidateId, CompleteUploadRequest req) {
        CandidateRow candidate = requireWritableCandidate(userId, candidateId);

        if ("MULTIPART".equals(candidate.getUploadMode())) {
            completeMultipartSession(candidate, req);
        }
        return verifyAndFinalize(candidate);
    }

    // ---------- 内部 ----------

    private void completeMultipartSession(CandidateRow candidate, CompleteUploadRequest req) {
        List<PartResult> parts;
        if (req == null || req.parts() == null) {
            parts = List.of();
        } else {
            parts = req.parts();
        }
        // ★ 核心：分片完整性规则——分片号必须从 1 开始连续且不重复。
        // S3 只校验"每个分片的 ETag 对得上"，不校验"分片是否齐"；
        // 缺片合并出的对象是"被截断的视频"，必须在应用层拦下。
        if (parts.isEmpty()) {
            throw new ApiException(ErrorCode.PARTS_INVALID, "缺少分片 ETag 列表");
        }
        List<Integer> numbers = parts.stream().map(PartResult::partNumber).sorted().toList();
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) != i + 1) {
                throw new ApiException(ErrorCode.PARTS_INVALID,
                        "分片号必须从 1 连续，收到 " + numbers);
            }
        }
        List<StoragePort.PartETag> etags = parts.stream()
                .map(p -> new StoragePort.PartETag(p.partNumber(), p.etag()))
                .sorted(java.util.Comparator.comparingInt(StoragePort.PartETag::partNumber))
                .toList();
        try {
            storage.completeMultipart(candidate.getObjectKey(), candidate.getS3UploadId(), etags);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_FOUND,
                    "分片会话无效（uploadId 过期或分片未全部上传）");
        }
    }

    private CompleteUploadResponse verifyAndFinalize(CandidateRow candidate) {
        StoragePort.HeadInfo head = storage.head(candidate.getObjectKey());
        if (!head.exists()) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_FOUND);
        }
        // 大小必须与登记一致——客户端谎报/传输截断都在这里现形
        if (head.contentLength() != candidate.getSizeBytes()) {
            String reason = "大小不匹配：登记 " + candidate.getSizeBytes()
                    + " 字节，实际 " + head.contentLength() + " 字节";
            candidates.markInvalid(candidate.getId(), reason);
            return new CompleteUploadResponse(candidate.getId(), "INVALID", reason);
        }
        String reason = MediaSignature.check(storage.rangeGet(candidate.getObjectKey(), 0, 16));
        if (reason != null) {
            candidates.markInvalid(candidate.getId(), reason);
            return new CompleteUploadResponse(candidate.getId(), "INVALID", reason);
        }
        candidates.markUploaded(candidate.getId(), S3StorageAdapter.normalizeEtag(head.etag()));
        return new CompleteUploadResponse(candidate.getId(), "UPLOADED", null);
    }

    private CandidateRow requireWritableCandidate(long userId, long candidateId) {
        CandidateRow candidate = candidates.findById(candidateId);
        if (candidate == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 候选 → 批次的归属链 + OWNER/OPERATOR 写权限（沿 F2 的统一入口）
        batchService.requireWritableBatch(userId, candidate.getBatchId());
        if (!"PENDING_UPLOAD".equals(candidate.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_UPLOAD_STATE,
                    "候选当前状态为 " + candidate.getStatus() + "，不能再上传/确认");
        }
        return candidate;
    }
}
