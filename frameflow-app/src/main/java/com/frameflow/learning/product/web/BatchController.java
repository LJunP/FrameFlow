package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.BatchProgressStreamService;
import com.frameflow.learning.product.service.BatchService;
import com.frameflow.learning.product.service.ReconcileService;
import com.frameflow.learning.product.web.BatchDtos.BatchResponse;
import com.frameflow.learning.product.web.BatchDtos.CandidateResponse;
import com.frameflow.learning.product.web.BatchDtos.CreateBatchRequest;
import com.frameflow.learning.product.web.BatchDtos.RegisterCandidateRequest;
import com.frameflow.learning.product.web.BatchDtos.RegisterCandidateResponse;
import com.frameflow.learning.product.web.BatchDtos.ReconcileResponse;
import com.frameflow.learning.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 批次接口：创建（绑定 Profile 版本 + Brief 快照）、关闭、候选登记、对账。
 */
@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchService batchService;
    private final ReconcileService reconcileService;
    private final BatchProgressStreamService progressStream;

    public BatchController(BatchService batchService, ReconcileService reconcileService,
                           BatchProgressStreamService progressStream) {
        this.batchService = batchService;
        this.reconcileService = reconcileService;
        this.progressStream = progressStream;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse create(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody CreateBatchRequest req) {
        return batchService.create(Long.parseLong(jwt.getSubject()), req);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return batchService.get(Long.parseLong(jwt.getSubject()), id);
    }

    /** F5：缓存优先的进度查询（轮询友好，含穿透防护哨兵）。 */
    @GetMapping("/{id}/progress")
    public java.util.Map<String, Integer> progress(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable long id) {
        return batchService.progressOf(Long.parseLong(jwt.getSubject()), id);
    }

    /**
     * F5+：批次分析进度的 SSE 实时推送（替代前端固定间隔轮询）。
     * 连接建立时即完成团队归属校验；计数变化推送 progress 事件，终态推送 done 并关闭连接。
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return progressStream.subscribe(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/{id}/close")
    public BatchResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return batchService.close(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/{id}/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterCandidateResponse registerCandidate(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable long id,
                                                       @Valid @RequestBody RegisterCandidateRequest req) {
        return batchService.registerCandidate(Long.parseLong(jwt.getSubject()), id, req);
    }

    @GetMapping("/{id}/candidates")
    public PageResponse<CandidateResponse> listCandidates(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable long id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size) {
        return batchService.listCandidates(Long.parseLong(jwt.getSubject()), id, page, size);
    }

    @PostMapping("/{id}/reconcile")
    public ReconcileResponse reconcile(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return reconcileService.reconcile(Long.parseLong(jwt.getSubject()), id);
    }
}
