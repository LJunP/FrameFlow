package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.domain.ActiveMemberView;
import com.frameflow.identity.domain.MembershipView;
import com.frameflow.identity.domain.TeamMember;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TeamMemberMapper {

    @Insert("INSERT INTO team_members (team_id, user_id, role, status, created_at, updated_at) "
            + "VALUES (#{teamId}, #{userId}, #{role}, #{status}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TeamMember member);

    @Select("SELECT id, team_id AS teamId, user_id AS userId, role, status, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM team_members WHERE team_id = #{teamId} AND user_id = #{userId}"
            + "  FOR UPDATE")
    TeamMember findByTeamAndUserForUpdate(@Param("teamId") long teamId, @Param("userId") long userId);

    @Select("SELECT id, team_id AS teamId, user_id AS userId, role, status, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM team_members WHERE team_id = #{teamId} AND user_id = #{userId} AND status = 'ACTIVE'")
    TeamMember findActiveByTeamAndUser(@Param("teamId") long teamId, @Param("userId") long userId);

    @Select("SELECT id, team_id AS teamId, user_id AS userId, role, status, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM team_members WHERE team_id = #{teamId} FOR UPDATE")
    List<TeamMember> lockMembersByTeam(@Param("teamId") long teamId);

    @Select("SELECT count(*) FROM team_members "
            + "WHERE team_id = #{teamId} AND status = 'ACTIVE' AND role = 'OWNER'")
    int countActiveOwners(@Param("teamId") long teamId);

    @Select("SELECT tm.id, tm.team_id AS teamId, tm.user_id AS userId, u.email, tm.role, tm.status, "
            + "tm.created_at AS createdAt "
            + "FROM team_members tm JOIN users u ON u.id = tm.user_id "
            + "WHERE tm.team_id = #{teamId} AND tm.status = 'ACTIVE' "
            + "ORDER BY tm.id")
    List<ActiveMemberView> listActiveByTeam(@Param("teamId") long teamId);

    @Select("SELECT tm.team_id AS teamId, t.name, tm.role, tm.status, t.created_at AS createdAt "
            + "FROM team_members tm JOIN teams t ON t.id = tm.team_id "
            + "WHERE tm.user_id = #{userId} AND tm.status = 'ACTIVE' "
            + "ORDER BY tm.id")
    List<MembershipView> listActiveMembershipsByUser(@Param("userId") long userId);

    @Update("UPDATE team_members SET role = #{role}, status = 'ACTIVE', updated_at = now() "
            + "WHERE team_id = #{teamId} AND user_id = #{userId}")
    int reactivateRemoved(@Param("teamId") long teamId, @Param("userId") long userId, @Param("role") String role);

    @Update("UPDATE team_members SET role = #{role}, updated_at = now() WHERE id = #{id}")
    int updateRole(@Param("id") long id, @Param("role") String role);

    @Update("UPDATE team_members SET status = #{status}, updated_at = now() WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
