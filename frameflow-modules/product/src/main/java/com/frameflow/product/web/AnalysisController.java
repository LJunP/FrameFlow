package com.frameflow.product.web;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.api.TeamGuard;
import com.frameflow.product.application.AnalysisService;
import com.frameflow.product.application.BatchService;
import com.frameflow.product.domain.AnalysisSelection.AnalysisRun;
import com.frameflow.product.domain.AnalysisSelection.Finding;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.web.dto.ProductDtos.IngestResultRequest;
import com.frameflow.product.web.dto.ProductDtos.StartAnalysisRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "product", description = "Analysis Run 与 Finding")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    private final AnalysisService service;
    private final BatchService batchService;
    private final TeamGuard guard;

    public AnalysisController(AnalysisService service, BatchService batchService, TeamGuard guard) {
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

    @PostMapping("/batches/{batchId}/analysis-runs")
    @Operation(operationId = "startAnalysis", summary = "启动分析（OWNER/OPERATOR；返回 202 + run id）")
    public ResponseEntity<Map<String, Object>> startAnalysis(Authentication auth, @PathVariable long batchId,
            @Valid @RequestBody StartAnalysisRequest req) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireOwnerOrOperator(uid, batch.getTeamId());
        long runId = service.startAnalysis(new AnalysisService.StartAnalysisCmd(
                batch.getTeamId(), batchId, req.candidateId(), uid));
        String commandId = service.commandForRun(runId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("runId", runId);
        result.put("commandId", commandId == null ? "cmd_pending" : commandId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/batches/{batchId}/analysis-runs")
    @Operation(operationId = "listAnalysisRuns", summary = "批次分析运行列表")
    public ResponseEntity<List<AnalysisRun>> listRuns(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = batchService.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(service.listRuns(batch.getTeamId(), batchId));
    }

    @GetMapping("/analysis-runs/{runId}")
    @Operation(operationId = "getAnalysisRun", summary = "获取分析运行")
    public ResponseEntity<AnalysisRun> getRun(Authentication auth, @PathVariable long runId) {
        long uid = userId(auth);
        AnalysisRun run = service.getRun(runId);
        guard.requireMember(uid, run.getTeamId());
        return ResponseEntity.ok(run);
    }

    @PostMapping("/analysis-runs/{runId}/cancel")
    @Operation(operationId = "cancelAnalysisRun", summary = "取消分析运行")
    public ResponseEntity<Void> cancelRun(Authentication auth, @PathVariable long runId) {
        long uid = userId(auth);
        AnalysisRun run = service.getRun(runId);
        guard.requireOwnerOrOperator(uid, run.getTeamId());
        service.cancelRun(run.getTeamId(), runId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/candidates/{candidateId}/findings")
    @Operation(operationId = "listCandidateFindings", summary = "候选 Findings 列表（时间码证据）")
    public ResponseEntity<List<Finding>> candidateFindings(Authentication auth, @PathVariable long candidateId) {
        long uid = userId(auth);
        var candidate = batchService.getCandidateByIdAnyTeam(candidateId);
        guard.requireMember(uid, candidate.getTeamId());
        return ResponseEntity.ok(service.listFindingsForCandidate(candidate.getTeamId(), candidateId));
    }

    @GetMapping("/analysis-runs/{runId}/findings")
    @Operation(operationId = "listRunFindings", summary = "运行 Findings")
    public ResponseEntity<List<Finding>> runFindings(Authentication auth, @PathVariable long runId) {
        long uid = userId(auth);
        AnalysisRun run = service.getRun(runId);
        guard.requireMember(uid, run.getTeamId());
        return ResponseEntity.ok(service.listFindingsForRun(run.getTeamId(), runId));
    }

    /** Worker result ingestion — idempotent by commandId + completed status. */
    @PostMapping("/internal/analysis-runs/{runId}/results")
    @Operation(operationId = "ingestAnalysisResult", summary = "Worker 结果摄取（幂等）")
    public ResponseEntity<Void> ingestResult(Authentication auth, @PathVariable long runId,
            @Valid @RequestBody IngestResultRequest req) {
        long uid = userId(auth);
        AnalysisRun run = service.getRun(runId);
        guard.requireMember(uid, run.getTeamId());
        List<Finding> findings = new ArrayList<>();
        if (req.findings() != null) {
            for (var d : req.findings()) {
                Finding f = new Finding();
                f.setRuleId(d.ruleId());
                f.setDetectorId(d.detectorId());
                f.setDetectorVersion(d.detectorVersion());
                f.setDimension(d.dimension());
                f.setFindingType(d.findingType());
                f.setVerdict(d.verdict());
                f.setSeverity(d.severity());
                f.setConfidence(d.confidence());
                f.setAutomationAction(d.automationAction() == null ? "REVIEW" : d.automationAction());
                f.setStartMs(d.startMs());
                f.setEndMs(d.endMs());
                f.setSummary(d.summary());
                f.setEvidence(d.evidence());
                f.setOrigin(d.origin());
                findings.add(f);
            }
        }
        service.ingestResult(run.getTeamId(), runId, req.commandId(), req.status(),
                req.decision(), req.qualityVector(), findings);
        return ResponseEntity.noContent().build();
    }
}