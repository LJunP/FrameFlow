package com.frameflow.product.web;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.api.TeamGuard;
import com.frameflow.product.application.BatchService;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.web.dto.ProductDtos.AddCandidatesRequest;
import com.frameflow.product.web.dto.ProductDtos.CompleteUploadRequest;
import com.frameflow.product.web.dto.ProductDtos.CreateBatchRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "product", description = "Batch 与 Candidate")
@SecurityRequirement(name = "bearerAuth")
public class BatchController {

    private final BatchService service;
    private final TeamGuard guard;

    public BatchController(BatchService service, TeamGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    private long userId(Authentication auth) {
        Object p = auth == null ? null : auth.getPrincipal();
        if (p instanceof com.frameflow.identity.api.IdentityPrincipal cu) {
            return cu.userId();
        }
        throw ApiException.forbidden("unauthenticated");
    }

    @PostMapping("/projects/{projectId}/batches")
    @Operation(operationId = "createBatch", summary = "创建批次（OWNER/OPERATOR）")
    public ResponseEntity<Map<String, Long>> createBatch(Authentication auth,
            @PathVariable long projectId, @Valid @RequestBody CreateBatchRequest req) {
        long uid = userId(auth);
        var project = service.projectOf(projectId);
        guard.requireOwnerOrOperator(uid, project.getTeamId());
        long id = service.createBatch(new BatchService.CreateBatchCmd(project.getTeamId(), projectId, uid,
                req.name(), req.promptText(), req.profileVersionId(), req.briefId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/projects/{projectId}/batches")
    @Operation(operationId = "listBatches", summary = "项目批次列表")
    public ResponseEntity<List<Batch>> listBatches(Authentication auth, @PathVariable long projectId) {
        long uid = userId(auth);
        var project = service.projectOf(projectId);
        guard.requireMember(uid, project.getTeamId());
        return ResponseEntity.ok(service.listBatches(project.getTeamId(), projectId));
    }

    @GetMapping("/batches/{batchId}")
    @Operation(operationId = "getBatch", summary = "获取批次")
    public ResponseEntity<Batch> getBatch(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = service.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(batch);
    }

    @PostMapping("/batches/{batchId}/candidates")
    @Operation(operationId = "addCandidates", summary = "批量添加候选（OWNER/OPERATOR）")
    public ResponseEntity<List<Long>> addCandidates(Authentication auth, @PathVariable long batchId,
            @Valid @RequestBody AddCandidatesRequest req) {
        long uid = userId(auth);
        Batch batch = service.getBatch(batchId);
        guard.requireOwnerOrOperator(uid, batch.getTeamId());
        List<Long> ids = service.addCandidates(new BatchService.AddCandidatesCmd(
                batch.getTeamId(), batchId, uid, req.keys(), req.mediaTypes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ids);
    }

    @GetMapping("/batches/{batchId}/candidates")
    @Operation(operationId = "listCandidates", summary = "批次候选列表")
    public ResponseEntity<List<Candidate>> listCandidates(Authentication auth, @PathVariable long batchId) {
        long uid = userId(auth);
        Batch batch = service.getBatch(batchId);
        guard.requireMember(uid, batch.getTeamId());
        return ResponseEntity.ok(service.listCandidates(batch.getTeamId(), batchId));
    }

    @PostMapping("/candidates/{candidateId}/upload-session")
    @Operation(operationId = "createUploadSession", summary = "创建上传会话")
    public ResponseEntity<Map<String, Object>> createUploadSession(Authentication auth,
            @PathVariable long candidateId) {
        long uid = userId(auth);
        Candidate c = service.getCandidateByIdAnyTeam(candidateId);
        guard.requireOwnerOrOperator(uid, c.getTeamId());
        long sessionId = service.createUploadSession(c.getTeamId(), candidateId, uid);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "status", "PENDING"));
    }

    @PostMapping("/candidates/{candidateId}/upload-complete")
    @Operation(operationId = "completeUpload", summary = "确认上传完成（校验大小/摘要）")
    public ResponseEntity<Candidate> completeUpload(Authentication auth, @PathVariable long candidateId,
            @Valid @RequestBody CompleteUploadRequest req) {
        long uid = userId(auth);
        Candidate c = service.getCandidateByIdAnyTeam(candidateId);
        guard.requireOwnerOrOperator(uid, c.getTeamId());
        Candidate updated = service.completeUpload(c.getTeamId(), req.sessionId(),
                req.sizeBytes() == null ? 0 : req.sizeBytes(), req.contentDigest());
        return ResponseEntity.ok(updated);
    }
}