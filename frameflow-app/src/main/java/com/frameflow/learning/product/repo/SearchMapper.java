package com.frameflow.learning.product.repo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 全局搜索的只读查询（项目 / 批次 / 候选三类命中）。
 *
 * ★ 核心：三条 SQL 各自都是"单条查询 + 按团队过滤"，绝不先查 id 再逐行回查
 * （N+1）。团队边界写在 WHERE 里：项目直接比 team_id；批次与候选没有
 * team_id，只能沿 候选 → 批次 → 项目 的外键链 JOIN 回团队，
 * 任何一环缺失都不会出现在结果里。
 *
 * ★ 核心：pattern 由 SearchService 转义后传入（% 与 _ 转义为字面量），
 * SQL 端显式声明 ESCAPE '\'，两边配合才能保证"用户输入什么就搜什么"。
 * 没有 ESCAPE 子句时 PostgreSQL 依赖默认反斜杠转义，行为随配置漂移。
 */
@Mapper
public interface SearchMapper {

    /** 项目命中：按名称或说明匹配，match 字段告诉前端命中的是哪一列。 */
    @Select("SELECT id, name, status, "
            + "CASE WHEN name ILIKE #{pattern} ESCAPE '\\' THEN 'name' ELSE 'description' END "
            + "AS matched_field, created_at "
            + "FROM projects WHERE team_id = #{teamId} "
            + "AND (name ILIKE #{pattern} ESCAPE '\\' "
            + "OR description ILIKE #{pattern} ESCAPE '\\') "
            + "ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<ProjectHit> searchProjects(@Param("teamId") Long teamId,
                                    @Param("pattern") String pattern,
                                    @Param("limit") int limit);

    /**
     * 批次命中：批次表没有名称列，可搜索的稳定语义是"它属于哪个项目"，
     * 因此按项目名匹配并回传项目名作为匹配上下文。
     */
    @Select("SELECT b.id, b.project_id, b.status, b.created_at, p.name AS project_name "
            + "FROM generation_batches b JOIN projects p ON p.id = b.project_id "
            + "WHERE p.team_id = #{teamId} "
            + "AND p.name ILIKE #{pattern} ESCAPE '\\' "
            + "ORDER BY b.created_at DESC, b.id DESC LIMIT #{limit}")
    List<BatchHit> searchBatches(@Param("teamId") Long teamId,
                                 @Param("pattern") String pattern,
                                 @Param("limit") int limit);

    /** 候选命中：按文件名匹配，batch_id 作为回跳父级。 */
    @Select("SELECT c.id, c.batch_id, c.file_name, c.status, c.created_at, b.project_id "
            + "FROM candidates c "
            + "JOIN generation_batches b ON b.id = c.batch_id "
            + "JOIN projects p ON p.id = b.project_id "
            + "WHERE p.team_id = #{teamId} "
            + "AND c.file_name ILIKE #{pattern} ESCAPE '\\' "
            + "ORDER BY c.created_at DESC, c.id DESC LIMIT #{limit}")
    List<CandidateHit> searchCandidates(@Param("teamId") Long teamId,
                                        @Param("pattern") String pattern,
                                        @Param("limit") int limit);

    /** 项目命中的行形状。 */
    class ProjectHit {
        private Long id;
        private String name;
        private String status;
        private String matchedField;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMatchedField() { return matchedField; }
        public void setMatchedField(String matchedField) { this.matchedField = matchedField; }
    }

    /** 批次命中的行形状。 */
    class BatchHit {
        private Long id;
        private Long projectId;
        private String status;
        private String projectName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
    }

    /** 候选命中的行形状。 */
    class CandidateHit {
        private Long id;
        private Long batchId;
        private String fileName;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
