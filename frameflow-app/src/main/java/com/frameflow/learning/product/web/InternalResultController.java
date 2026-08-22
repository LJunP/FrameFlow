package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.AnalysisIngestionService;
import com.frameflow.learning.product.service.AnalysisIngestionService.IngestionResponse;
import com.frameflow.learning.product.service.AnalysisIngestionService.ResultRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：worker 回写分析结果（由 InternalAuthFilter 的 X-Worker-Key 保护）。
 * 对外契约已登记（docs/api），但它不是给浏览器用的。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalResultController {

    private final AnalysisIngestionService ingestion;

    public InternalResultController(AnalysisIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/analysis-results")
    public IngestionResponse submitResult(@Valid @RequestBody ResultRequest req) {
        return ingestion.ingest(req);
    }
}
