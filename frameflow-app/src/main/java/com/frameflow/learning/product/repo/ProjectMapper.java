package com.frameflow.learning.product.repo;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** projects 表访问（含分页与乐观锁更新）。 */
@Mapper
public interface ProjectMapper {

    @Select("INSERT INTO projects(team_id, name, description, created_by) "
            + "VALUES(#{teamId}, #{name}, #{description}, #{createdBy}) RETURNING id")
    Long insert(@Param("teamId") Long teamId,
                @Param("name") String name,
                @Param("description") String description,
                @Param("createdBy") Long createdBy);

    @Select("SELECT id, team_id, name, description, status, current_brief_id, "
            + "lock_version, created_by, created_at, updated_at FROM projects WHERE id = #{id}")
    ProjectRow findById(Long id);

    // ★ 核心：LIMIT/OFFSET 分页——写法最直观，但 OFFSET 是"跳过前 N 条"，
    // 页码越深扫描越多（第 100 页要数过前 99 页）。本项目项目数规模下完全
    // 够用；将来列表变深时换 keyset 分页（WHERE id > 最后一条的 id LIMIT n），
    // 每页成本恒定。排序用 id 保证分页稳定（否则两次翻页可能重复/漏行）。
    @Select("SELECT id, team_id, name, description, status, current_brief_id, "
            + "lock_version, created_by, created_at, updated_at "
            + "FROM projects WHERE team_id = #{teamId} ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<ProjectRow> findByTeamPage(@Param("teamId") Long teamId,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Select("SELECT count(*) FROM projects WHERE team_id = #{teamId}")
    long countByTeam(Long teamId);

    // ★ 核心：乐观锁的 UPDATE——WHERE 里同时匹配 id 和"客户端声称的版本"，
    // 并顺手自增 lock_version。影响行数 = 0 有两种含义（数据不存在 / 版本
    // 过期），由 Service 先查存在性再区分（见 ProjectService.update 的 ★）。
    @Update("UPDATE projects SET name = #{name}, description = #{description}, "
            + "lock_version = lock_version + 1, updated_at = now() "
            + "WHERE id = #{id} AND lock_version = #{expectedLockVersion}")
    int update(@Param("id") Long id,
               @Param("name") String name,
               @Param("description") String description,
               @Param("expectedLockVersion") int expectedLockVersion);

    /** 归档：仅 ACTIVE 且版本匹配时可归档（幂等重复归档由 0 行区分）。 */
    @Update("UPDATE projects SET status = 'ARCHIVED', "
            + "lock_version = lock_version + 1, updated_at = now() "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND lock_version = #{expectedLockVersion}")
    int archive(@Param("id") Long id, @Param("expectedLockVersion") int expectedLockVersion);

    /** 新快照发布后把"当前指针"前移（可变的是指针，不可变的是快照行）。 */
    @Update("UPDATE projects SET current_brief_id = #{briefId}, "
            + "lock_version = lock_version + 1, updated_at = now() WHERE id = #{projectId}")
    int pointCurrentBriefTo(@Param("projectId") Long projectId, @Param("briefId") Long briefId);
}
