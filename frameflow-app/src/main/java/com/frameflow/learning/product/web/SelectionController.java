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
            // ★ 核心：前缀 UTF-8 BOM——Excel 靠它识别"这是 UTF-8"，
            // 否则中文表头会按本地代码页乱码。BOM 只出现一次，在文件最开头。
            StringBuilder sb = new StringBuilder("\uFEFF");
            sb.append(CSV_HEADER).append('\n');
            for (ExportRow r : rows) {
                sb.append(r.rank()).append(',')
                        .append(r.candidateId()).append(',')
                        .append(csv(r.fileName())).append(',')
                        .append(csv(r.status())).append(',')
                        .append(r.sizeBytes()).append(',')
                        .append(csv(r.contentType())).append(',')
                        .append(r.compositeScore()).append(',')
                        .append(r.machinePick() ? "是" : "否").append(',')
                        .append(r.clusterId() == null ? "" : r.clusterId()).append(',')
                        .append(csv(r.verdict())).append(',')
                        .append(csv(r.reviewAction())).append(',')
                        .append(csv(r.uploadedAt())).append(',')
                        .append(csv(r.exportedAt())).append('\n');
            }
            // 文件名带优选集 id 与日期：同一批次多次导出不会互相覆盖
            String fileName = "selection-" + id + "-"
                    + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    + ".csv";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=utf-8"))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
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

    /** CSV 表头（中文，与产品界面一致）；顺序与 ExportRow 字段一一对应。 */
    private static final String CSV_HEADER =
            "排名,候选ID,文件名,状态,大小(字节),内容类型,综合得分,机器入选,簇编号,质检结论,人工复核动作,上传时间,导出时间";

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
