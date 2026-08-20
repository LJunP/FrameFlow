package com.frameflow.product.web;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.api.TeamGuard;
import com.frameflow.product.application.BatchService;
import com.frameflow.product.application.SelectionService;
import com.frameflow.product.domain.AnalysisSelection.RankingEntry;
import com.frameflow.product.domain.AnalysisSelection.RankingSnapshot;
import com.frameflow.product.domain.AnalysisSelection.SelectionSet;
import com.frameflow.product.domain.AnalysisSelection.SimilarityCluster;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.web.dto.ProductDtos.ClusterRequest;
import com.frameflow.product.web.dto.ProductDtos.CreateSelectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "product", description = "聚类、排名与选择集")
@SecurityRequirement(name = "bearerAuth")
public class SelectionController {

    private final SelectionService service;
    private final BatchService batchService;
    private final TeamGuard guard;

    public SelectionController(SelectionService service, BatchService batchService, TeamGuard guard) {
        this.service = service;
        this.batchService = batchService;
        this.guard = guard;
    }

    private long userId(Authentication auth) {
        Object p = auth == null ? null : auth.getPrincipal();
        if (p instanceof com.frameflow.identity.api.IdentityPrincipal cu) {
            return cu.userId();
        }
        throw ApiException.forbidden("unauthenticated");
    }

    @PostMapping("/batches/{batchId}/similarity-clusters")
    @Operation(operationId = "createSimilarityClusters", summary = "计算候选相似度聚类")
    public ResponseEntity<List<SimilarityCluster>> cluster(Authentication auth, @PathVariable long batchId,
            @Valid @RequestBody(required = false) ClusterRequest req) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        double threshold = req == null || req.threshold() == null ? 0.85 : req.threshold();
        List<SimilarityCluster> clusters = service.clusterBatch(batch.getTeamId(), batchId, threshold);
        return ResponseEntity.status(HttpStatus.CREATED).body(clusters);
    }

    @GetMapping("/batches/{batchId}/similarity-clusters")
    @Operation(operationId = "listSimilarityClusters", summary = "批次聚类列表")
    public ResponseEntity<List<SimilarityCluster>> listClusters(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(service.listClusters(batch.getTeamId(), batchId));
    }

    @PostMapping("/batches/{batchId}/ranking-snapshots")
    @Operation(operationId = "createRankingSnapshot", summary = "生成排名快照")
    public ResponseEntity<RankingSnapshot> rank(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        RankingSnapshot snapshot = service.rankBatch(batch.getTeamId(), batchId, uid);
        return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
    }

    @GetMapping("/batches/{batchId}/ranking-snapshots")
    @Operation(operationId = "listRankingSnapshots", summary = "批次排名快照列表")
    public ResponseEntity<List<Map<String, Object>>> listRankings(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(service.listRankings(batch.getTeamId(), batchId));
    }

    @GetMapping("/ranking-snapshots/{snapshotId}")
    @Operation(operationId = "getRankingSnapshot", summary = "排名快照（含分值构成）")
    public ResponseEntity<List<RankingEntry>> getRanking(Authentication auth, @PathVariable long snapshotId) {
        long uid = userId(auth);
        RankingSnapshot s = service.getRanking(snapshotId);
        guard.requireMember(uid, s.getBatchId() == null ? -1L : batchService.getBatch(s.getBatchId()).getTeamId());
        return ResponseEntity.ok(service.rankingEntries(0L, snapshotId));
    }

    @PostMapping("/batches/{batchId}/selection-sets")
    @Operation(operationId = "createSelectionSet", summary = "创建选择集（Top-K）")
    public ResponseEntity<SelectionSet> createSelection(Authentication auth, @PathVariable long batchId,
            @Valid @RequestBody CreateSelectionRequest req) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        int topK = req.topK() == null ? 10 : req.topK();
        SelectionSet s = service.createSelection(batch.getTeamId(), batchId, uid, req.name(), topK);
        return ResponseEntity.status(HttpStatus.CREATED).body(s);
    }

    @GetMapping("/batches/{batchId}/selection-sets")
    @Operation(operationId = "listSelectionSets", summary = "批次选择集列表")
    public ResponseEntity<List<SelectionSet>> listSelections(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(service.listSelections(batch.getTeamId(), batchId));
    }

    @PostMapping("/selection-sets/{selectionSetId}/lock")
    @Operation(operationId = "lockSelectionSet", summary = "锁定选择集（不可变）")
    public ResponseEntity<SelectionSet> lock(Authentication auth, @PathVariable long selectionSetId,
            @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        service.getSelection(teamId, selectionSetId);
        guard.requireMember(uid, teamId);
        return ResponseEntity.ok(service.lockSelection(teamId, selectionSetId, uid));
    }

    @GetMapping("/selection-sets/{selectionSetId}/export")
    @Operation(operationId = "exportSelectionSet", summary = "导出选择集（CSV/JSON）")
    public ResponseEntity<Map<String, Object>> export(Authentication auth, @PathVariable long selectionSetId,
            @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        SelectionSet s = service.getSelection(teamId, selectionSetId);
        guard.requireMember(uid, teamId);
        List<Long> ids = service.selectionCandidateIds(teamId, selectionSetId);
        return ResponseEntity.ok(Map.of("selectionSetId", selectionSetId, "status", s.getStatus(),
                "candidateIds", ids, "format", "csv-json"));
    }
}