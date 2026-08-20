package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.application.port.out.TeamMemberRepository;
import com.frameflow.identity.domain.ActiveMemberView;
import com.frameflow.identity.domain.MembershipView;
import com.frameflow.identity.domain.TeamMember;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis-backed team-member persistence adapter. */
@Repository
public class TeamMemberRepositoryAdapter implements TeamMemberRepository {

    private final TeamMemberMapper mapper;

    public TeamMemberRepositoryAdapter(TeamMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(TeamMember member) { return mapper.insert(member); }
    @Override public TeamMember findByTeamAndUserForUpdate(long teamId, long userId) {
        return mapper.findByTeamAndUserForUpdate(teamId, userId);
    }
    @Override public TeamMember findActiveByTeamAndUser(long teamId, long userId) {
        return mapper.findActiveByTeamAndUser(teamId, userId);
    }
    @Override public List<TeamMember> lockMembersByTeam(long teamId) { return mapper.lockMembersByTeam(teamId); }
    @Override public int countActiveOwners(long teamId) { return mapper.countActiveOwners(teamId); }
    @Override public List<ActiveMemberView> listActiveByTeam(long teamId) { return mapper.listActiveByTeam(teamId); }
    @Override public List<MembershipView> listActiveMembershipsByUser(long userId) {
        return mapper.listActiveMembershipsByUser(userId);
    }
    @Override public int reactivateRemoved(long teamId, long userId, String role) {
        return mapper.reactivateRemoved(teamId, userId, role);
    }
    @Override public int updateRole(long id, String role) { return mapper.updateRole(id, role); }
    @Override public int updateStatus(long id, String status) { return mapper.updateStatus(id, status); }
}
