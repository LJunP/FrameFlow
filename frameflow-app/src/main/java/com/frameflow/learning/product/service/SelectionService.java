package com.frameflow.learning.product.service;

import java.util.List;

import com.frameflow.learning.product.repo.RankingEntryRow;
import com.frameflow.learning.product.repo.RankingMapper;
import com.frameflow.learning.product.repo.SelectionMapper;
import com.frameflow.learning.product.repo.SelectionRow;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优选集：机器 Top-K 打底 → 人工叠加调整 → 锁定导出。
 *
 * ★ 人工调整不改写机器结果（docs/01 §9）：machine_pick 与 human_action
 * 两列并存——导出时各表各的，事后审计能完整还原"机器选了什么、人改了什么"。
 */
@Service
public class SelectionService {

    private final BatchService batchService;
    private final RankingService rankingService;
    private final RankingMapper rankings;
    private final SelectionMapper selections;

    public SelectionService(BatchService batchService, RankingService rankingService,
                            RankingMapper rankings, SelectionMapper selections) {
        this.batchService = batchService;
        this.rankingService = rankingService;
        this.rankings = rankings;
        this.selections = selections;
    }

    @Transactional
    public com.frameflow.learning.product.web.RankingDtos.SelectionSummary create(long userId, long batchId, int topK, Long snapshotId) {
        batchService.requireWritableBatch(userId, batchId);
        long snapshot = snapshotId != null ? snapshotId
                : orThrow(rankings.latestSnapshotId(batchId), "该批次还没有排名快照");
        var snapshotRow = rankings.findSnapshot(snapshot);
        if (snapshotRow == null || snapshotRow.getBatchId() != batchId) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "快照不存在或不属于该批次");
        }
        int safeK = Math.min(Math.max(topK, 1), 100);
        Long id = selections.insertSet(batchId, snapshot, safeK, userId);
        for (RankingEntryRow entry : rankings.entriesBySnapshot(snapshot)) {
            if (entry.getRankNo() >= 1 && entry.getRankNo() <= safeK) {
                selections.insertItem(id, entry.getCandidateId(), true, null, null);
            }
        }
        return summary(selections.findSet(id));
    }

    @Transactional
    public void adjust(long userId, long selectionId, long candidateId,
                       String action, String note) {
        requireWritableSelection(userId, selectionId);
        if (!"INCLUDE".equals(action) && !"EXCLUDE".equals(action)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "action 只能是 INCLUDE/EXCLUDE");
        }
        // 已有条目 → 叠加调整；没有 → 追加（EXCLUDE 一个机器没选的等于记录否决意见）
        int updated = selections.adjustItem(selectionId, candidateId, action, note);
        if (updated == 0) {
            selections.addItemIfDraft(selectionId, candidateId, action, note);
        }
    }

    @Transactional
    public com.frameflow.learning.product.web.RankingDtos.SelectionSummary lock(
            long userId, long selectionId) {
        requireWritableSelection(userId, selectionId);
        if (selections.lockIfDraft(selectionId, userId) == 0) {
            throw new ApiException(ErrorCode.SELECTION_LOCKED);
        }
        return summary(selections.findSet(selectionId));
    }

    private com.frameflow.learning.product.web.RankingDtos.SelectionSummary summary(
            SelectionRow set) {
        return new com.frameflow.learning.product.web.RankingDtos.SelectionSummary(
                set.getId(), set.getBatchId(), set.getSnapshotId(), set.getStatus(),
                set.getTopK(), set.getLockedAt() == null ? null : set.getLockedAt().toString());
    }

    public com.frameflow.learning.product.web.RankingDtos.SelectionDetail get(
            long userId, long selectionId) {
        SelectionRow set = requireSelectionOfMyTeam(userId, selectionId);
        return detail(set);
    }

    private com.frameflow.learning.product.web.RankingDtos.SelectionDetail detail(
            SelectionRow set) {
        var items = selections.itemsBySelection(set.getId()).stream()
                .map(i -> new com.frameflow.learning.product.web.RankingDtos.SelectionItemResponse(
                        i.getCandidateId(), i.isMachinePick(), i.getHumanAction(), i.getNote()))
                .toList();
        return new com.frameflow.learning.product.web.RankingDtos.SelectionDetail(
                set.getId(), set.getBatchId(), set.getSnapshotId(),
                set.getStatus(), set.getTopK(), items);
    }

    public List<com.frameflow.learning.product.web.RankingDtos.SelectionSummary> listByBatch(
            long userId, long batchId) {
        batchService.requireBatchOfMyTeam(userId, batchId);
        return selections.listByBatch(batchId).stream().map(set ->
                new com.frameflow.learning.product.web.RankingDtos.SelectionSummary(
                        set.getId(), set.getBatchId(), set.getSnapshotId(),
                        set.getStatus(), set.getTopK(),
                        set.getLockedAt() == null ? null : set.getLockedAt().toString()))
                .toList();
    }


    /** 导出行：优选项 × 候选元数据 × 排名（单次 JOIN 查询，无 N+1；仅锁定后可导出）。 */
    public List<ExportRow> exportRows(long userId, long selectionId) {
        SelectionRow set = requireSelectionOfMyTeam(userId, selectionId);
        if (!"LOCKED".equals(set.getStatus())) {
            throw new ApiException(ErrorCode.NOT_LOCKED);
        }
        // 导出时刻整批共用：同一份交付文件里所有行的时间戳必须一致
        String exportedAt = java.time.OffsetDateTime.now().toString();
        return selections.exportItems(selectionId, set.getSnapshotId()).stream()
                .map(i -> new ExportRow(
                        i.getRankNo() == null ? 0 : i.getRankNo(),
                        i.getCandidateId(),
                        i.getFileName(),
                        i.getStatus(),
                        i.getSizeBytes() == null ? 0L : i.getSizeBytes(),
                        i.getContentType(),
                        i.getCompositeScore() == null ? 0 : i.getCompositeScore(),
                        i.isMachinePick(),
                        i.getClusterId(),
                        verdictOf(i.getStatus()),
                        i.getHumanAction(),
                        i.getUploadedAt() == null ? null : i.getUploadedAt().toString(),
                        exportedAt))
                .toList();
    }

    /**
     * ★ 核心：verdict 是"候选级质检结论"。candidates 表没有独立 verdict 列，
     * 又不能为了导出改 schema，唯一权威来源就是候选终态 status：
     * 资格门只放行 ANALYZED（确定性全过）与 REVIEW_REQUIRED（语义存疑待人工）；
     * AUTO_REJECT / ANALYSIS_ERROR / INVALID 一律原样透出——尤其 ANALYSIS_ERROR
     * 必须以自己的名字出现，绝不能被改写成"视频不合格"（红线：ANALYSIS_ERROR
     * 不得伪装成视频不合格）。
     */
    private static String verdictOf(String status) {
        return "ANALYZED".equals(status) ? "PASS" : status;
    }

    /**
     * 导出行。★ 核心：machinePick 与 clusterId 必须保留——F7 的交付原则是
     * "机器 Top-K 与人工调整并存可审计"，只导出人工动作会让交付文件丢失
     * "这条是谁选进来的"证据，事后无法复盘机器与人工的分歧。
     * clusterId 在候选不在排名快照内（纯人工 INCLUDE）时为 null。
     */
    public record ExportRow(int rank, long candidateId, String fileName, String status,
                            long sizeBytes, String contentType, int compositeScore,
                            boolean machinePick, Integer clusterId,
                            String verdict, String reviewAction, String uploadedAt,
                            String exportedAt) {
    }

    // ---------- 内部 ----------

    private SelectionRow requireSelectionOfMyTeam(long userId, long selectionId) {
        SelectionRow set = selections.findSet(selectionId);
        if (set == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        batchService.requireBatchOfMyTeam(userId, set.getBatchId());
        return set;
    }

    private void requireWritableSelection(long userId, long selectionId) {
        SelectionRow set = requireSelectionOfMyTeam(userId, selectionId);
        batchService.requireWritableBatch(userId, set.getBatchId());
        if ("LOCKED".equals(set.getStatus())) {
            throw new ApiException(ErrorCode.SELECTION_LOCKED);
        }
    }

    private static long orThrow(Long value, String message) {
        if (value == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, message);
        }
        return value;
    }
}
