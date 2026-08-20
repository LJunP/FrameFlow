package com.frameflow.product.web;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.api.TeamGuard;
import com.frameflow.product.application.BatchOrchestrator;
import com.frameflow.product.application.BatchService;
import com.frameflow.product.domain.BatchCandidate.Batch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "product", description = "批次引擎：进度、处理、重跑")
@SecurityRequirement(name = "bearerAuth")
public class BatchEngineController {

    private final BatchOrchestrator orchestrator;
    private final BatchService batchService;
    private final TeamGuard guard;

    public BatchEngineController(BatchOrchestrator orchestrator, BatchService batchService,
                                 TeamGuard guard) {
        this.orchestrator = orchestrator;
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

    @PostMapping("/batches/{batchId}/process")
    @Operation(operationId = "processBatch", summary = "处理批次全部候选（OWNER/OPERATOR）")
    public ResponseEntity<Map<String, Object>> processBatch(Authentication auth,
            @PathVariable long batchId, @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireOwnerOrOperator(uid, batch.getTeamId());
        var p = orchestrator.processBatch(batch.getTeamId(), batchId, uid);
        return ResponseEntity.ok(Map.of("batchId", p.batchId(), "candidateStatuses",
                p.candidateStatuses(), "total", p.total(), "failed", p.failed(), "analyzed", p.analyzed()));
    }

    @PostMapping("/batches/{batchId}/progress")
    @Operation(operationId = "batchProgress", summary = "批次处理进度（候选状态计数）")
    public ResponseEntity<Map<String, Object>> progress(Authentication auth,
            @PathVariable long batchId, @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        var p = orchestrator.progress(batch.getTeamId(), batchId);
        return ResponseEntity.ok(Map.of("batchId", p.batchId(), "candidateStatuses",
                p.candidateStatuses(), "total", p.total(), "failed", p.failed(), "analyzed", p.analyzed()));
    }

    @PostMapping("/candidates/{candidateId}/rerun")
    @Operation(operationId = "rerunCandidate", summary = "重跑候选分析（创建新 Analysis Run）")
    public ResponseEntity<Map<String, Object>> rerun(Authentication auth,
            @PathVariable long candidateId, @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        var candidate = batchService.getCandidateByIdAnyTeam(candidateId);
        guard.requireOwnerOrOperator(uid, candidate.getTeamId());
        long runId = orchestrator.rerunCandidate(candidate.getTeamId(), candidate.getBatchId(),
                candidateId, uid);
        return ResponseEntity.accepted().body(Map.of("runId", runId, "mode", "process"));
    }
}
