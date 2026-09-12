package com.frameflow.learning.product.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.service.TeamAccessService;
import com.frameflow.learning.product.repo.BatchMapper;
import com.frameflow.learning.product.repo.BatchRow;
import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.ProjectMapper;
import com.frameflow.learning.product.repo.ProjectRow;
import com.frameflow.learning.product.repo.QualityProfileMapper;
import com.frameflow.learning.product.repo.QualityProfileVersionRow;
import com.frameflow.learning.product.storage.StoragePort;
import com.frameflow.learning.product.storage.StorageProperties;
import com.frameflow.learning.product.web.BatchDtos.BatchResponse;
import com.frameflow.learning.product.web.BatchDtos.CreateBatchRequest;
import com.frameflow.learning.product.web.BatchDtos.RegisterCandidateRequest;
import com.frameflow.learning.product.web.BatchDtos.RegisterCandidateResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 批次：创建（绑定快照）、关闭、候选登记（发放直传凭证）。
 */
@Service
public class BatchService {

    private static final long MAX_OBJECT_BYTES = 5_000_000_000L;   // S3 单对象 5GB 上限

    private final BatchMapper batches;
    private final CandidateMapper candidates;
    private final ProjectMapper projects;
    private final QualityProfileMapper profiles;
    private final TeamAccessService teamAccess;
    private final StoragePort storage;
    private final StorageProperties storageProps;
    private final Clock clock;
    private final ProgressCacheService progressCache;

    public BatchService(BatchMapper batches, CandidateMapper candidates,
                        ProjectMapper projects, QualityProfileMapper profiles,
                        TeamAccessService teamAccess, StoragePort storage,
                        StorageProperties storageProps, Clock clock,
                        ProgressCacheService progressCache) {
        this.batches = batches;
        this.candidates = candidates;
        this.projects = projects;
        this.profiles = profiles;
        this.teamAccess = teamAccess;
        this.storage = storage;
        this.storageProps = storageProps;
        this.clock = clock;
        this.progressCache = progressCache;
    }

    @Transactional
    public BatchResponse create(long userId, CreateBatchRequest req) {
        ProjectRow project = projects.findById(req.projectId());
        if (project == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        teamAccess.requireRole(userId, project.getTeamId(), Role.OWNER, Role.OPERATOR);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED);
        }
        // ★ 核心：批次绑定"具体版本"——profileVersionNo 不传则解析为该 profile
        // 的最新版本。绑定后批次永远引用这一行（V3 迁移的 ★ 注释），
        // 后续发布新标准不影响任何已创建批次。
        // ★ 核心：配置与项目必须属于同一团队，不能只验证项目权限；否则可绑定他人配置。
        var profile = profiles.findProfileById(req.profileId());
        if (profile == null || !project.getTeamId().equals(profile.getTeamId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        QualityProfileVersionRow version = resolveVersion(req);
        if (project.getCurrentBriefId() == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                    "项目还没有 Brief 快照，请先发布一条 Brief 再创建批次");
        }
        Long batchId = batches.insert(project.getId(), version.getId(),
                project.getCurrentBriefId(), req.capacity(), userId);
        return get(userId, batchId);
    }

    public BatchResponse get(long userId, long batchId) {
        BatchRow batch = requireBatchOfMyTeam(userId, batchId);
        // F5：进度统计走 Cache-Aside——热批次的轮询不再每次打库；
        // TTL 30s + 写后失效双保险（见 ProgressCacheService ★ 注释）
        Map<String, Integer> counts = progressCache.getOrLoad(batchId, () -> countsFromDb(batchId));
        QualityProfileVersionRow version = profiles.findVersionById(batch.getProfileVersionId());
        return toResponse(batch, version.getVersionNo(), counts);
    }

    /** 项目下的批次列表（历史批次入口）：成员可读，新批次在前。 */
    public List<BatchResponse> listByProject(long userId, long projectId) {
        ProjectRow project = projects.findById(projectId);
        if (project == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        teamAccess.requireMember(userId, project.getTeamId());
        return batches.listByProject(projectId).stream()
                .map(row -> get(userId, row.getId()))
                .toList();
    }

    private Map<String, Integer> countsFromDb(long batchId) {
        Map<String, Integer> counts = new HashMap<>();
        for (CandidateMapper.StatusCount sc : candidates.countByStatus(batchId)) {
            counts.put(sc.getStatus(), sc.getCnt());
        }
        return counts;
    }

    /** F5：缓存仅复用统计值，权限始终按当前用户实时检查。 */
    public Map<String, Integer> progressOf(long userId, long batchId) {
        // ★ 核心：授权不能藏在缓存 loader 内，否则命中共享缓存时会跨团队泄露统计。
        // 不存在的批次保留原有空响应契约；哨兵只缓存空结果，不替代归属检查。
        BatchRow row = batches.findById(batchId);
        if (row != null) {
            teamAccess.requireMember(userId, batchTeamId(row));
        }
        return progressCache.getOrLoad(batchId, () -> row == null ? null : countsFromDb(batchId));
    }

    @Transactional
    public BatchResponse close(long userId, long batchId) {
        BatchRow batch = requireBatchOfMyTeam(userId, batchId);
        teamAccess.requireRole(userId, batchTeamId(batch), Role.OWNER, Role.OPERATOR);
        batches.closeIfOpen(batchId);   // 幂等：已关闭不影响结果
        return get(userId, batchId);
    }

    /**
     * 候选登记：校验批次与容量，决定 SIMPLE/MULTIPART 模式并发放直传凭证。
     */
    @Transactional
    public RegisterCandidateResponse registerCandidate(long userId, long batchId,
                                                       RegisterCandidateRequest req) {
        requireWritableBatch(userId, batchId);
        // ★ 核心：批次行锁将容量检查、登记及关闭串行化；仅加事务仍会并发超额。
        BatchRow batch = batches.findByIdForUpdate(batchId);
        if (!"OPEN".equals(batch.getStatus())) {
            throw new ApiException(ErrorCode.BATCH_CLOSED);
        }
        // 容量只数"活的"候选（PENDING/UPLOADED），INVALID 不占坑——
        // 坏文件重传换新登记，不该被自己污染的坑位卡死
        if (candidates.countActive(batchId) >= batch.getCapacity()) {
            throw new ApiException(ErrorCode.BATCH_FULL);
        }
        if (!req.contentType().startsWith("video/")) {
            throw new ApiException(ErrorCode.INVALID_CONTENT_TYPE);
        }
        if (req.sizeBytes() > MAX_OBJECT_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "文件超出单对象上限 " + MAX_OBJECT_BYTES + " 字节");
        }

        boolean multipart = req.sizeBytes() > storageProps.multipartThreshold().toBytes();
        // ★ 核心：按服务端真实阈值检查客户端能力，配置变化也不能留下无法上传的占位。
        if (multipart && Boolean.TRUE.equals(req.simpleOnly())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "文件超过当前单文件直传上限，请使用分片上传客户端");
        }
        String objectKey = "batches/" + batchId + "/candidates/" + "c" + System.nanoTime()
                + "/" + sanitize(req.fileName());
        Long candidateId = candidates.insert(batchId, sanitize(req.fileName()),
                req.contentType(), req.sizeBytes(), objectKey, userId);
        // ★ 核心：提交后再失效，避免并发读在提交前把旧计数重新写入缓存。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { progressCache.evict(batchId); }
        });

        // ★ 核心：先落库再发凭证，S3 调用失败时事务回滚不留孤儿行；
        // 反向顺序（先 S3 后落库）失败会留下无主对象。注意 S3 的分片会话
        // 不参与数据库事务——initiate 成功后若后续异常，必须在 catch 里
        // 显式 abort，否则 MinIO 会积累"有头无尾"的分片会话占磁盘。
        if (multipart) {
            String uploadId = null;
            try {
                uploadId = storage.initiateMultipart(objectKey);
                String sessionId = uploadId;
                // 数据库最终提交失败也要补偿已创建的外部会话，而不仅是方法内抛错。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try { storage.abortMultipart(objectKey, sessionId); } catch (Exception ignored) { }
                        }
                    }
                });
                candidates.updateUploadSession(candidateId, "MULTIPART", uploadId);
            } catch (Exception e) {
                if (uploadId != null) {
                    try { storage.abortMultipart(objectKey, uploadId); } catch (Exception ignore) { }
                }
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "无法发起分片上传");
            }
            return new RegisterCandidateResponse(candidateId, "MULTIPART", null, uploadId,
                    storageProps.partSize().toBytes(), OffsetDateTime.now(clock)
                            .plus(storageProps.presignTtl()).toString());
        }
        String uploadUrl = storage.presignPut(objectKey, storageProps.presignTtl());
        candidates.updateUploadSession(candidateId, "SIMPLE", null);
        return new RegisterCandidateResponse(candidateId, "SIMPLE", uploadUrl, null,
                0, OffsetDateTime.now(clock).plus(storageProps.presignTtl()).toString());
    }

    // ---------- 内部 ----------

    public com.frameflow.learning.shared.api.PageResponse<com.frameflow.learning.product.web.BatchDtos.CandidateResponse> listCandidates(
            long userId, long batchId, int page, int size) {
        requireBatchOfMyTeam(userId, batchId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        var items = candidates.listByBatchPage(batchId, safeSize, safePage * safeSize)
                .stream().map(BatchService::toCandidateResponse).toList();
        long total = candidates.countByStatus(batchId).stream()
                .mapToLong(CandidateMapper.StatusCount::getCnt).sum();
        return com.frameflow.learning.shared.api.PageResponse.of(items, safePage, safeSize, total);
    }

    private static com.frameflow.learning.product.web.BatchDtos.CandidateResponse toCandidateResponse(
            com.frameflow.learning.product.repo.CandidateRow row) {
        return new com.frameflow.learning.product.web.BatchDtos.CandidateResponse(
                row.getId(), row.getBatchId(), row.getStatus(), row.getFileName(),
                row.getContentType(), row.getSizeBytes(), row.getObjectKey(),
                row.getUploadMode(), row.getEtag(), row.getProbeError());
    }

    private QualityProfileVersionRow resolveVersion(CreateBatchRequest req) {
        Integer no = req.profileVersionNo();
        QualityProfileVersionRow version = (no == null)
                ? latestVersion(req.profileId())
                : profiles.findVersion(req.profileId(), no);
        if (version == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                    no == null ? "该 profile 没有任何版本" : "指定的 profile 版本不存在");
        }
        return version;
    }

    private QualityProfileVersionRow latestVersion(long profileId) {
        Integer max = profiles.maxVersionNo(profileId);
        return max == null ? null : profiles.findVersion(profileId, max);
    }

    /**
     * 批次 → 项目 → 团队的归属链；任何一环缺失/越权都 404（防枚举一致语义）。
     */
    public BatchRow requireBatchOfMyTeam(long userId, long batchId) {
        BatchRow batch = batches.findById(batchId);
        if (batch == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        teamAccess.requireMember(userId, batchTeamId(batch));
        return batch;
    }

    /** 写操作（登记候选/确认上传/对账）统一走这里：成员 + OWNER/OPERATOR。 */
    public BatchRow requireWritableBatch(long userId, long batchId) {
        BatchRow batch = requireBatchOfMyTeam(userId, batchId);
        teamAccess.requireRole(userId, batchTeamId(batch), Role.OWNER, Role.OPERATOR);
        return batch;
    }

    private long batchTeamId(BatchRow batch) {
        ProjectRow project = projects.findById(batch.getProjectId());
        return project.getTeamId();
    }

    /** 文件名净化：去路径分隔符与奇怪字符，只留 URL 友好字符。 */
    private String sanitize(String fileName) {
        String name = fileName.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private BatchResponse toResponse(BatchRow batch, Integer versionNo, Map<String, Integer> counts) {
        return new BatchResponse(batch.getId(), batch.getProjectId(),
                batch.getProfileVersionId(), versionNo, batch.getBriefId(),
                batch.getStatus(), batch.getCapacity(), counts);
    }
}
