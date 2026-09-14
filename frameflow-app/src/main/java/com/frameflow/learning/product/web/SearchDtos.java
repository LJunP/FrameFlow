package com.frameflow.learning.product.web;

import java.util.List;

/**
 * 全局搜索接口 DTO。
 *
 * ★ 核心：分组返回而不是把三类结果拍平——前端要按类型分区渲染并各自回跳
 * 不同详情页（/projects、/batches、/candidates），分组省掉了客户端按类型
 * 归并，也避免"同一 id 不同实体"的歧义。
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /** 项目命中：matchedField 指明命中 name 还是 description（匹配上下文）。 */
    public record ProjectHit(Long id, String name, String status, String matchedField) {
    }

    /** 批次命中：projectId 是回跳父级，projectName 是匹配上下文。 */
    public record BatchHit(Long id, Long projectId, String status, String projectName) {
    }

    /** 候选命中：batchId 是回跳父级（前端据此进入 /candidates/{id}）。 */
    public record CandidateHit(Long id, Long batchId, String fileName, String status) {
    }

    public record SearchResponse(List<ProjectHit> projects, List<BatchHit> batches,
                                 List<CandidateHit> candidates) {
    }
}
