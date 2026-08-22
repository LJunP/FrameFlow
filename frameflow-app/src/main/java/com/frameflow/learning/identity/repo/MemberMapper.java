package com.frameflow.learning.identity.repo;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** team_members 表访问（成员与角色）。 */
@Mapper
public interface MemberMapper {

    @Insert("INSERT INTO team_members(team_id, user_id, role) "
            + "VALUES(#{teamId}, #{userId}, #{role})")
    int insert(@Param("teamId") Long teamId,
               @Param("userId") Long userId,
               @Param("role") String role);

    /** 查不到返回 null（MyBatis 对空结果集的约定行为），调用方据此判断 404。 */
    @Select("SELECT team_id, user_id, role FROM team_members "
            + "WHERE user_id = #{userId} AND team_id = #{teamId}")
    MemberRow findByUserAndTeam(@Param("userId") Long userId, @Param("teamId") Long teamId);

    /** F1 阶段一个用户只属于一个团队：取其第一个成员关系拿到 teamId+role。 */
    @Select("SELECT team_id, user_id, role FROM team_members "
            + "WHERE user_id = #{userId} ORDER BY team_id LIMIT 1")
    MemberRow findFirstByUser(Long userId);

    /** 成员列表（联表取用户展示信息；不查 password_hash，见 TeamMemberView 注释）。 */
    @Select("SELECT m.user_id, u.email, u.display_name, m.role "
            + "FROM team_members m JOIN users u ON u.id = m.user_id "
            + "WHERE m.team_id = #{teamId} ORDER BY m.created_at")
    List<TeamMemberView> listByTeam(Long teamId);
}
