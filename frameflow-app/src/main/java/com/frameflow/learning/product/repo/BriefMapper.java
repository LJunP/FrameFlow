package com.frameflow.learning.product.repo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * briefs 表访问——注意整个 Mapper 【没有任何 @Update/@Delete】：
 * 不可变数据只允许插入与查询，"不能改"由代码结构保证而不只是靠约定。
 */
@Mapper
public interface BriefMapper {

    @Select("INSERT INTO briefs(project_id, content, created_by) "
            + "VALUES(#{projectId}, #{content}, #{createdBy}) RETURNING id")
    Long insert(@Param("projectId") Long projectId,
                @Param("content") String content,
                @Param("createdBy") Long createdBy);

    @Select("SELECT id, project_id, content, created_by, created_at FROM briefs "
            + "WHERE project_id = #{projectId} ORDER BY id")
    List<BriefRow> listByProject(Long projectId);

    @Select("SELECT id, project_id, content, created_by, created_at FROM briefs "
            + "WHERE id = #{id}")
    BriefRow findById(Long id);
}
