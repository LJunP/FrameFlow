package com.frameflow.learning.product.web;

import java.util.List;

/** 排名与优选的接口 DTO（web 层不暴露 repo 行对象——ArchitectureTest 强制）。 */
public final class RankingDtos {

    private RankingDtos() {
    }

    public record RankingEntryResponse(long candidateId, int rankNo, int clusterId,
                                       boolean representative, int score,
                                       String breakdown, String excludedReason) {
    }

    public record LatestRankingResponse(Long snapshotId, String algorithmVersion,
                                        int hammingThreshold,
                                        List<RankingEntryResponse> entries) {
    }

    public record SelectionSummary(long id, long batchId, long snapshotId, String status,
                                   int topK, String lockedAt) {
    }

    public record SelectionItemResponse(long candidateId, boolean machinePick,
                                        String humanAction, String note) {
    }

    public record SelectionDetail(long id, long batchId, long snapshotId, String status,
                                  int topK, List<SelectionItemResponse> items) {
    }
}
