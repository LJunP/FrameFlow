package com.frameflow.identity.application.port.out;

import com.frameflow.identity.domain.ActiveMemberView;
import com.frameflow.identity.domain.MembershipView;
import com.frameflow.identity.domain.TeamMember;
import java.util.List;

/** Persistence port for team membership and its read projections. */
public interface TeamMemberRepository {
    int insert(TeamMember member);
    TeamMember findByTeamAndUserForUpdate(long teamId, long userId);
    TeamMember findActiveByTeamAndUser(long teamId, long userId);
    List<TeamMember> lockMembersByTeam(long teamId);
    int countActiveOwners(long teamId);
    List<ActiveMemberView> listActiveByTeam(long teamId);
    List<MembershipView> listActiveMembershipsByUser(long userId);
    int reactivateRemoved(long teamId, long userId, String role);
    int updateRole(long id, String role);
    int updateStatus(long id, String status);
}
