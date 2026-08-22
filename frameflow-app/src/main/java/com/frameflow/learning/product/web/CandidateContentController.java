package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.AnalysisQueryService;
import com.frameflow.learning.product.web.AnalysisDtos.ContentUrlResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * F8：候选媒体播放地址——审阅页 <video> 的源（短时效 presigned GET）。
 */
@RestController
public class CandidateContentController {

    private final AnalysisQueryService queryService;

    public CandidateContentController(AnalysisQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/candidates/{id}/content-url")
    public ContentUrlResponse contentUrl(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long id) {
        return queryService.contentUrlOf(Long.parseLong(jwt.getSubject()), id);
    }
}
