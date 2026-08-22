package com.frameflow.learning.product.web;

import java.util.List;

import com.frameflow.learning.product.service.AnalysisDispatchService;
import com.frameflow.learning.product.service.AnalysisDispatchService.DispatchResult;
import com.frameflow.learning.product.service.AnalysisQueryService;
import com.frameflow.learning.product.web.AnalysisDtos.FindingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 批次分析与 Finding 查询接口。 */
@RestController
public class AnalysisController {

    private final AnalysisDispatchService dispatchService;
    private final AnalysisQueryService queryService;

    public AnalysisController(AnalysisDispatchService dispatchService,
                              AnalysisQueryService queryService) {
        this.dispatchService = dispatchService;
        this.queryService = queryService;
    }

    @PostMapping("/api/v1/batches/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DispatchResult analyze(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return dispatchService.dispatchBatch(Long.parseLong(jwt.getSubject()), id);
    }

    /** 候选的完整 Finding 列表（含 passed 的检查证据）。 */
    @GetMapping("/api/v1/candidates/{id}/findings")
    public List<FindingResponse> findings(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable long id) {
        return queryService.findingsOf(Long.parseLong(jwt.getSubject()), id);
    }
}
