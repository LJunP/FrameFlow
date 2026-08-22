package com.frameflow.learning.product.service;

import java.util.ArrayList;
import java.util.List;

import com.frameflow.learning.product.repo.RankingEntryRow;
import com.frameflow.learning.product.repo.RankingMapper;
import com.frameflow.learning.product.repo.SelectionMapper;
import com.frameflow.learning.product.repo.SelectionMapper.SelectionItemRow;
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


    /** 导出行：排名信息 × 优选项 × 候选信息 三方合并（仅锁定后可导出）。 */
    public List<ExportRow> exportRows(long userId, long selectionId) {
        SelectionRow set = requireSelectionOfMyTeam(userId, selectionId);
        if (!"LOCKED".equals(set.getStatus())) {
            throw new ApiException(ErrorCode.NOT_LOCKED);
        }
        List<RankingEntryRow> entries = rankings.entriesBySnapshot(set.getSnapshotId());
        var entryByCandidate = new java.util.HashMap<Long, RankingEntryRow>();
        for (RankingEntryRow e : entries) {
            entryByCandidate.put(e.getCandidateId(), e);
        }
        List<ExportRow> rows = new ArrayList<>();
        for (SelectionItemRow item : selections.itemsBySelection(selectionId)) {
            RankingEntryRow entry = entryByCandidate.get(item.getCandidateId());
            rows.add(new ExportRow(
                    entry == null ? 0 : entry.getRankNo(),
                    item.getCandidateId(),
                    entry == null ? 0 : entry.getScore(),
                    entry == null ? 0 : entry.getClusterId(),
                    item.isMachinePick(),
                    item.getHumanAction(),
                    item.getNote()));
        }
        rows.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
        return rows;
    }

    public record ExportRow(int rank, long candidateId, int score, int clusterId,
                            boolean machinePick, String humanAction, String note) {
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
