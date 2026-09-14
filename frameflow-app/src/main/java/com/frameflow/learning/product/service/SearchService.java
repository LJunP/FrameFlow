package com.frameflow.learning.product.service;

import com.frameflow.learning.identity.service.TeamAccessService;
import com.frameflow.learning.product.repo.SearchMapper;
import com.frameflow.learning.product.repo.SearchMapper.BatchHit;
import com.frameflow.learning.product.repo.SearchMapper.CandidateHit;
import com.frameflow.learning.product.repo.SearchMapper.ProjectHit;
import com.frameflow.learning.product.web.SearchDtos;
import com.frameflow.learning.product.web.SearchDtos.SearchResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 全局搜索（项目 / 批次 / 候选）。
 *
 * 权限口径与其他读接口一致：团队成员都能搜（OWNER/OPERATOR/REVIEWER/VIEWER），
 * 但结果集永远被裁剪到"调用者所在团队"——搜索是横向扫描器，越权后果
 * 比单个详情接口更严重，因此团队边界在 SQL 层强制，而不是拿结果再过滤。
 *
 * 【阅读顺序】本类 → SearchMapper（三条团队作用域的 SQL）→ SearchController。
 */
@Service
public class SearchService {

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final SearchMapper search;
    private final TeamAccessService teamAccess;

    public SearchService(SearchMapper search, TeamAccessService teamAccess) {
        this.search = search;
        this.teamAccess = teamAccess;
    }

    public SearchResponse search(long userId, long teamId, String q, Integer limit) {
        // ★ 核心：先鉴权再查库——非本团队成员直接 404（与其他接口同样的
        // 防枚举语义），绝不允许"先搜出结果再判断归属"。
        teamAccess.requireMember(userId, teamId);

        String keyword = q == null ? "" : q.trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "搜索关键词至少 2 个字符");
        }
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        String pattern = likePattern(keyword);
        return new SearchResponse(
                search.searchProjects(teamId, pattern, safeLimit).stream()
                        .map(SearchService::toProjectHit).toList(),
                search.searchBatches(teamId, pattern, safeLimit).stream()
                        .map(SearchService::toBatchHit).toList(),
                search.searchCandidates(teamId, pattern, safeLimit).stream()
                        .map(SearchService::toCandidateHit).toList());
    }

    // ★ 核心：把用户输入变成"字面量通配模式"——先转义反斜杠本身，再转义
    // LIKE 的两个元字符 % 与 _，最后才补上两端 %。顺序不能反：先替换 % 再
    // 替换反斜杠，会把刚加上的转义反斜杠再转义一次，模式全乱。
    // 不转义时用户输入 "_" 会变成"任意单字符"，搜 "a_c" 会命中 "abc"——
    // 这不是报错而是静默返回错误结果，最难排查。
    private static String likePattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static SearchDtos.ProjectHit toProjectHit(ProjectHit row) {
        return new SearchDtos.ProjectHit(row.getId(), row.getName(), row.getStatus(),
                row.getMatchedField());
    }

    private static SearchDtos.BatchHit toBatchHit(BatchHit row) {
        return new SearchDtos.BatchHit(row.getId(), row.getProjectId(), row.getStatus(),
                row.getProjectName());
    }

    private static SearchDtos.CandidateHit toCandidateHit(CandidateHit row) {
        return new SearchDtos.CandidateHit(row.getId(), row.getBatchId(), row.getFileName(),
                row.getStatus());
    }
}
