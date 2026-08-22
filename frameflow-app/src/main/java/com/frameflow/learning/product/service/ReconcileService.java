package com.frameflow.learning.product.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.CandidateRow;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.product.storage.StorageProperties;
import com.frameflow.learning.product.web.BatchDtos.ReconcileResponse;
import org.springframework.stereotype.Service;

/**
 * T4 对账：字节在 MinIO、元数据在 PG，两套存储必然存在漂移窗口——
 * 客户端拿了凭证没上传、上传了没确认、对象被误删、直接写进 bucket 的
 * 无主对象。对账就是定期把两边对齐并把"说不清的"变成"说得清的"。
 */
@Service
public class ReconcileService {

    private static final int SAMPLE_LIMIT = 20;

    private final BatchService batchService;
    private final CandidateMapper candidates;
    private final StoragePort storage;
    private final StorageProperties storageProps;
    private final Clock clock;
    private final ProgressCacheService progressCache;

    public ReconcileService(BatchService batchService, CandidateMapper candidates,
                            StoragePort storage, StorageProperties storageProps, Clock clock,
                            ProgressCacheService progressCache) {
        this.batchService = batchService;
        this.candidates = candidates;
        this.storage = storage;
        this.storageProps = storageProps;
        this.clock = clock;
        this.progressCache = progressCache;
    }

    public ReconcileResponse reconcile(long userId, long batchId) {
        batchService.requireWritableBatch(userId, batchId);

        // 第一步：超时未完成的登记直接判 INVALID（带证据）——
        // 这是"对账要能修数"的最小修复动作，其余异常只报告不动手
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(storageProps.uploadSessionTtl());
        int invalidated = candidates.invalidateStalePending(batchId, cutoff);
        // ★ 修 bug：对账改了候选状态（PENDING→INVALID），必须同步失效进度缓存——
        // 这里曾是 F5"写后失效"清单漏掉的第 4 个变更点，导致进度端点
        // 最长 30 秒展示过期计数（有专门回归测试盯着）
        if (invalidated > 0) {
            progressCache.evict(batchId);
        }

        int awaitingUpload = 0;
        int uploadedNotCompleted = 0;
        List<String> missingObjectKeys = new ArrayList<>();
        Set<String> knownKeys = new HashSet<>();

        for (CandidateRow candidate : candidates.listByBatch(batchId)) {
            knownKeys.add(candidate.getObjectKey());
            switch (candidate.getStatus()) {
                case "PENDING_UPLOAD" -> {
                    // 还在等直传：对象却已存在 = 客户端传完了但没来确认
                    if (storage.head(candidate.getObjectKey()).exists()) {
                        uploadedNotCompleted++;
                    } else {
                        awaitingUpload++;
                    }
                }
                // ★ 核心：UPLOADED 却查无对象 = 最严重的不一致
                //（元数据说有、字节没了）——线上应触发告警而不是静默改状态，
                // 所以这里只报告，不自动降级状态（避免掩盖存储层故障）。
                case "UPLOADED" -> {
                    if (!storage.head(candidate.getObjectKey()).exists()) {
                        missingObjectKeys.add(candidate.getObjectKey());
                    }
                }
                default -> { /* INVALID：无需核对（对象可能保留作证据） */ }
            }
        }

        // 第二步：bucket 里有、数据库没有的 = 孤儿对象（绕过 API 直写的产物）
        List<String> orphanKeys = new ArrayList<>();
        for (String key : storage.listKeys("batches/" + batchId + "/")) {
            if (!knownKeys.contains(key) && orphanKeys.size() < SAMPLE_LIMIT) {
                orphanKeys.add(key);
            }
        }

        return new ReconcileResponse(batchId, OffsetDateTime.now(clock).toString(),
                awaitingUpload, uploadedNotCompleted,
                missingObjectKeys.size(), orphanKeys.size(), invalidated,
                missingObjectKeys, orphanKeys);
    }
}
