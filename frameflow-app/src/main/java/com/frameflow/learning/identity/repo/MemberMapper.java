package com.frameflow.learning.identity.repo;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 当前会话团队：取该用户<strong>最近加入</strong>的成员关系。
     * 邀请入团后 login/refresh 必须落在受邀团队，而不是 id 最小的那个旧团队。
     * 完整多团队切换 UI 另议；在此之前用"最近加入"作为唯一当前上下文。
     */
    @Select("SELECT team_id, user_id, role FROM team_members "
            + "WHERE user_id = #{userId} ORDER BY created_at DESC, team_id DESC LIMIT 1")
    MemberRow findFirstByUser(Long userId);

    @Select("SELECT m.team_id, m.user_id, m.role FROM team_members m "
            + "WHERE m.user_id = #{userId} ORDER BY m.created_at DESC, m.team_id DESC")
    List<MemberRow> listByUser(Long userId);

    /** 成员列表（联表取用户展示信息；不查 password_hash，见 TeamMemberView 注释）。 */
    @Select("SELECT m.user_id, u.email, u.display_name, m.role "
            + "FROM team_members m JOIN users u ON u.id = m.user_id "
            + "WHERE m.team_id = #{teamId} ORDER BY m.created_at")
    List<TeamMemberView> listByTeam(Long teamId);

    /** 变更角色：返回受影响行数——0 意味着成员关系不存在，调用方据此判 404。 */
    @Update("UPDATE team_members SET role = #{role} "
            + "WHERE team_id = #{teamId} AND user_id = #{userId}")
    int updateRole(@Param("teamId") Long teamId,
                   @Param("userId") Long userId,
                   @Param("role") String role);

    /** 移除成员：返回受影响行数（0 = 成员不存在）。用户记录本身保留，只断成员关系。 */
    @Delete("DELETE FROM team_members "
            + "WHERE team_id = #{teamId} AND user_id = #{userId}")
    int delete(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
