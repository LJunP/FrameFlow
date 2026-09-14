package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.SearchService;
import com.frameflow.learning.product.web.SearchDtos.SearchResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索接口。
 *
 * 与 ProjectController 同口径：teamId 只从 JWT 的 tid claim 取，不信任
 * 任何请求参数里的团队归属——搜索是跨实体扫描，越权读取的爆炸半径更大。
 * Controller 保持"薄"：清洗、鉴权、团队过滤全在 SearchService。
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** q 用 required=false 交给 Service 判空：缺参直接经过校验分支返回 400，而不是容器级 500。 */
    @GetMapping
    public SearchResponse search(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) Integer limit) {
        return searchService.search(Long.parseLong(jwt.getSubject()), jwt.getClaim("tid"), q, limit);
    }
}
