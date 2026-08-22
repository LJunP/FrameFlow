package com.frameflow.learning.product.web;

import java.util.List;

import com.frameflow.learning.product.service.SelectionService;
import com.frameflow.learning.product.service.SelectionService.ExportRow;
import com.frameflow.learning.product.web.RankingDtos.SelectionDetail;
import com.frameflow.learning.product.web.RankingDtos.SelectionSummary;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 优选集接口：创建（Top-K 打底）→ 人工叠加 → 锁定 → 导出。 */
@RestController
public class SelectionController {

    private final SelectionService selectionService;
    private final ObjectMapper objectMapper;

    public SelectionController(SelectionService selectionService, ObjectMapper objectMapper) {
        this.selectionService = selectionService;
        this.objectMapper = objectMapper;
    }

    public record CreateSelectionRequest(@NotNull @Min(1) @Max(100) Integer topK,
                                         Long snapshotId) {
    }

    public record AdjustItemRequest(@NotNull Long candidateId,
                                    @NotNull String action,
                                    String note) {
    }

    @PostMapping("/api/v1/batches/{id}/selections")
    @ResponseStatus(HttpStatus.CREATED)
    public SelectionSummary create(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                   @Valid @RequestBody CreateSelectionRequest req) {
        return selectionService.create(Long.parseLong(jwt.getSubject()), id,
                req.topK(), req.snapshotId());
    }

    @GetMapping("/api/v1/batches/{id}/selections")
    public List<SelectionSummary> listByBatch(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable long id) {
        return selectionService.listByBatch(Long.parseLong(jwt.getSubject()), id);
    }

    @GetMapping("/api/v1/selections/{id}")
    public SelectionDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return selectionService.get(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/api/v1/selections/{id}/items")
    public SelectionDetail adjust(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable long id,
                                  @Valid @RequestBody AdjustItemRequest req) {
        selectionService.adjust(Long.parseLong(jwt.getSubject()), id,
                req.candidateId(), req.action(), req.note());
        return get(jwt, id);
    }

    @PostMapping("/api/v1/selections/{id}/lock")
    public SelectionSummary lock(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return selectionService.lock(Long.parseLong(jwt.getSubject()), id);
    }

    /** 导出：仅 LOCKED；format=json|csv。 */
    @GetMapping("/api/v1/selections/{id}/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long id,
                                         @RequestParam(defaultValue = "json") String format) {
        List<ExportRow> rows = selectionService.exportRows(
                Long.parseLong(jwt.getSubject()), id);
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder(
                    "rank,candidate_id,score,cluster_id,machine_pick,human_action,note\n");
            for (ExportRow r : rows) {
                sb.append(r.rank()).append(',')
                        .append(r.candidateId()).append(',')
                        .append(r.score()).append(',')
                        .append(r.clusterId()).append(',')
                        .append(r.machinePick()).append(',')
                        .append(csv(r.humanAction())).append(',')
                        .append(csv(r.note())).append('\n');
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(sb.toString());
        }
        if (!"json".equalsIgnoreCase(format)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "format 只支持 json/csv");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(rows));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "导出序列化失败");
        }
    }

    /** CSV 最小引号原则：仅当值含逗号/引号/换行才包裹（任何解析器都兼容）。 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }
}
