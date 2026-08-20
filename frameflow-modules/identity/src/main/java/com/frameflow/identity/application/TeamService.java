package com.frameflow.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.application.model.TeamModels.AddMemberCommand;
import com.frameflow.identity.application.model.TeamModels.CreateTeamCommand;
import com.frameflow.identity.application.model.TeamModels.MemberListView;
import com.frameflow.identity.application.model.TeamModels.TeamListView;
import com.frameflow.identity.application.model.TeamModels.TeamMemberView;
import com.frameflow.identity.application.model.TeamModels.TeamMembershipView;
import com.frameflow.identity.application.model.TeamModels.TeamView;
import com.frameflow.identity.application.model.TeamModels.UpdateMemberRoleCommand;
import com.frameflow.identity.application.port.out.TeamMemberRepository;
import com.frameflow.identity.application.port.out.TeamRepository;
import com.frameflow.identity.domain.ActiveMemberView;
import com.frameflow.identity.domain.MembershipView;
import com.frameflow.identity.domain.Team;
import com.frameflow.identity.domain.TeamMember;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.error.ErrorCodes;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Team and membership management with real-time membership/role authorization. */
@Service
@ConditionalOnWebApplication
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ActiveUserPolicy activeUsers;
    private final IdempotencyService idempotencyService;
    private final RequestFingerprint fingerprint;
    private final ObjectMapper objectMapper;

    public TeamService(TeamRepository teams, TeamMemberRepository members, ActiveUserPolicy activeUsers,
                       IdempotencyService idempotencyService, RequestFingerprint fingerprint,
                       ObjectMapper objectMapper) {
        this.teams = teams;
        this.members = members;
        this.activeUsers = activeUsers;
        this.idempotencyService = idempotencyService;
        this.fingerprint = fingerprint;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TeamListView listMyTeams(long userId) {
        activeUsers.requireActive(userId);
        List<MembershipView> views = members.listActiveMembershipsByUser(userId);
        List<TeamMembershipView> items = views.stream().map(v ->
                new TeamMembershipView(v.getTeamId(), v.getName(), v.getRole(), "ACTIVE", v.getCreatedAt())).toList();
        return new TeamListView(items);
    }

    public record CreateOutcome(TeamView view, String rawJson) {
    }

    @Transactional
    public IdempotencyExecution<CreateOutcome> createTeam(
            long userId, CreateTeamCommand request, UUID idempotencyKey) {
        activeUsers.requireActive(userId);
        String scope = "user:" + userId + ":POST:/api/v1/teams";
        String hash = fingerprint.compute("POST", "/api/v1/teams", request);
        return idempotencyService.execute(scope, idempotencyKey, hash,
                () -> doCreateTeam(userId, request),
                outcome -> new StoredResponse(201, outcome.rawJson()));
    }

    private CreateOutcome doCreateTeam(long userId, CreateTeamCommand request) {
        Team team = new Team();
        team.setName(request.name().trim());
        team.setCreatedBy(userId);
        teams.insert(team);
        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        members.insert(owner);
        TeamView view = new TeamView(team.getId(), team.getName(), team.getCreatedAt());
        return new CreateOutcome(view, json(view));
    }

    @Transactional(readOnly = true)
    public TeamView getTeam(long userId, long teamId) {
        activeUsers.requireActive(userId);
        requireActiveMembership(userId, teamId);
        Team team = teams.findById(teamId);
        if (team == null) {
            throw ApiException.notFound("团队不存在或无权访问");
        }
        return new TeamView(team.getId(), team.getName(), team.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public MemberListView listMembers(long userId, long teamId) {
        activeUsers.requireActive(userId);
        requireActiveMembership(userId, teamId);
        List<ActiveMemberView> views = members.listActiveByTeam(teamId);
        List<TeamMemberView> items = views.stream().map(v ->
                new TeamMemberView(v.getId(), v.getTeamId(), v.getUserId(), v.getEmail(),
                        v.getRole(), "ACTIVE")).toList();
        return new MemberListView(items);
    }

    public record MemberOutcome(TeamMemberView view, String rawJson) {
    }

    @Transactional
    public IdempotencyExecution<MemberOutcome> addMember(
            long userId, long teamId, AddMemberCommand request, UUID idempotencyKey) {
        activeUsers.requireActive(userId);
        String scope = "user:" + userId + ":POST:/api/v1/teams/" + teamId + "/members";
        String hash = fingerprint.compute("POST", "/api/v1/teams/" + teamId + "/members", request);
        return idempotencyService.execute(scope, idempotencyKey, hash,
                () -> doAddMember(userId, teamId, request),
                outcome -> new StoredResponse(201, outcome.rawJson()));
    }

    private MemberOutcome doAddMember(long userId, long teamId, AddMemberCommand request) {
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以添加成员");
        }
        String email = UserService.normalizeEmail(request.email());
        com.frameflow.identity.domain.User target = activeUsers.requireActiveTarget(email);
        String role = request.role();
        TeamMember existing = members.findByTeamAndUserForUpdate(teamId, target.getId());
        TeamMemberView view;
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            throw ApiException.conflict(ErrorCodes.TEAM_MEMBER_ALREADY_EXISTS, "该用户已经是此团队的 ACTIVE 成员");
        }
        if (existing != null) {
            members.reactivateRemoved(teamId, target.getId(), role);
            view = new TeamMemberView(existing.getId(), teamId, target.getId(), email, role, "ACTIVE");
        } else {
            TeamMember member = new TeamMember();
            member.setTeamId(teamId);
            member.setUserId(target.getId());
            member.setRole(role);
            member.setStatus("ACTIVE");
            try {
                members.insert(member);
            } catch (DataIntegrityViolationException ex) {
                // Different idempotency keys may race for the same unique
                // (team_id,user_id); the database winner is 201 and all losers are 409.
                throw ApiException.conflict(ErrorCodes.TEAM_MEMBER_ALREADY_EXISTS,
                        "该用户已经是此团队的 ACTIVE 成员");
            }
            view = new TeamMemberView(member.getId(), teamId, target.getId(), email, role, "ACTIVE");
        }
        return new MemberOutcome(view, json(view));
    }

    @Transactional
    public TeamMemberView updateRole(
            long userId, long teamId, long targetUserId, UpdateMemberRoleCommand request) {
        activeUsers.requireActive(userId);
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以修改成员角色");
        }
        TeamMember target = members.findActiveByTeamAndUser(teamId, targetUserId);
        if (target == null) {
            throw ApiException.notFound("成员不存在或无权访问");
        }
        String newRole = request.role();
        if ("OWNER".equals(target.getRole()) && !"OWNER".equals(newRole)) {
            members.lockMembersByTeam(teamId);
            if (members.countActiveOwners(teamId) <= 1) {
                throw ApiException.conflict(ErrorCodes.TEAM_LAST_OWNER_CONFLICT, "团队必须至少保留一名 ACTIVE OWNER");
            }
        }
        members.updateRole(target.getId(), newRole);
        com.frameflow.identity.domain.User targetUser = activeUsers.requireActive(targetUserId);
        return new TeamMemberView(target.getId(), teamId, targetUserId, targetUser.getEmail(),
                newRole, "ACTIVE");
    }

    @Transactional
    public void removeMember(long userId, long teamId, long targetUserId) {
        activeUsers.requireActive(userId);
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以移除成员");
        }
        if (userId == targetUserId) {
            throw ApiException.conflict(ErrorCodes.TEAM_SELF_REMOVAL_FORBIDDEN, "OWNER 不能通过成员删除接口移除自己");
        }
        TeamMember target = members.findActiveByTeamAndUser(teamId, targetUserId);
        if (target == null) {
            throw ApiException.notFound("成员不存在或无权访问");
        }
        members.lockMembersByTeam(teamId);
        if ("OWNER".equals(target.getRole()) && members.countActiveOwners(teamId) <= 1) {
            throw ApiException.conflict(ErrorCodes.TEAM_LAST_OWNER_CONFLICT, "团队必须至少保留一名 ACTIVE OWNER");
        }
        members.updateStatus(target.getId(), "REMOVED");
    }

    private TeamMember requireActiveMembership(long userId, long teamId) {
        TeamMember member = members.findActiveByTeamAndUser(teamId, userId);
        if (member == null) {
            throw ApiException.notFound("团队不存在或无权访问");
        }
        return member;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }
}
