package com.frameflow.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.domain.ActiveMemberView;
import com.frameflow.identity.domain.MembershipView;
import com.frameflow.identity.domain.Team;
import com.frameflow.identity.domain.TeamMember;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.error.ErrorCodes;
import com.frameflow.identity.infrastructure.persistence.TeamMapper;
import com.frameflow.identity.infrastructure.persistence.TeamMemberMapper;
import com.frameflow.identity.infrastructure.persistence.UserMapper;
import com.frameflow.identity.web.dto.AddMemberRequest;
import com.frameflow.identity.web.dto.CreateTeamRequest;
import com.frameflow.identity.web.dto.MemberList;
import com.frameflow.identity.web.dto.MemberStatus;
import com.frameflow.identity.web.dto.TeamList;
import com.frameflow.identity.web.dto.TeamMembershipSummary;
import com.frameflow.identity.web.dto.TeamRole;
import com.frameflow.identity.web.dto.UpdateMemberRoleRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Team and membership management with real-time membership/role authorization. */
@Service
@ConditionalOnWebApplication
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final IdempotencyService idempotencyService;
    private final RequestFingerprint fingerprint;
    private final ObjectMapper objectMapper;

    public TeamService(TeamMapper teamMapper, TeamMemberMapper memberMapper, UserMapper userMapper,
                       IdempotencyService idempotencyService, RequestFingerprint fingerprint,
                       ObjectMapper objectMapper) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.idempotencyService = idempotencyService;
        this.fingerprint = fingerprint;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TeamList listMyTeams(long userId) {
        List<MembershipView> views = memberMapper.listActiveMembershipsByUser(userId);
        List<TeamMembershipSummary> items = views.stream().map(v ->
                new TeamMembershipSummary(v.getTeamId(), v.getName(),
                        TeamRole.valueOf(v.getRole()), MemberStatus.ACTIVE, v.getCreatedAt())).toList();
        return new TeamList(items);
    }

    public record CreateOutcome(com.frameflow.identity.web.dto.Team dto, String rawJson) {
    }

    @Transactional
    public IdempotencyExecution<CreateOutcome> createTeam(long userId, CreateTeamRequest request, UUID idempotencyKey) {
        String scope = "user:" + userId + ":POST:/api/v1/teams";
        String hash = fingerprint.compute("POST", "/api/v1/teams", request);
        return idempotencyService.execute(scope, idempotencyKey, hash,
                () -> doCreateTeam(userId, request),
                outcome -> new StoredResponse(201, outcome.rawJson()));
    }

    private CreateOutcome doCreateTeam(long userId, CreateTeamRequest request) {
        Team team = new Team();
        team.setName(request.name().trim());
        team.setCreatedBy(userId);
        teamMapper.insert(team);
        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        memberMapper.insert(owner);
        com.frameflow.identity.web.dto.Team dto =
                new com.frameflow.identity.web.dto.Team(team.getId(), team.getName(), team.getCreatedAt());
        return new CreateOutcome(dto, json(dto));
    }

    @Transactional(readOnly = true)
    public com.frameflow.identity.web.dto.Team getTeam(long userId, long teamId) {
        requireActiveMembership(userId, teamId);
        Team team = teamMapper.findById(teamId);
        if (team == null) {
            throw ApiException.notFound("团队不存在或无权访问");
        }
        return new com.frameflow.identity.web.dto.Team(team.getId(), team.getName(), team.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public MemberList listMembers(long userId, long teamId) {
        requireActiveMembership(userId, teamId);
        List<ActiveMemberView> views = memberMapper.listActiveByTeam(teamId);
        List<com.frameflow.identity.web.dto.TeamMember> items = views.stream().map(v ->
                new com.frameflow.identity.web.dto.TeamMember(
                        v.getId(), v.getTeamId(), v.getUserId(), v.getEmail(),
                        TeamRole.valueOf(v.getRole()), MemberStatus.ACTIVE)).toList();
        return new MemberList(items);
    }

    public record MemberOutcome(com.frameflow.identity.web.dto.TeamMember dto, String rawJson) {
    }

    @Transactional
    public IdempotencyExecution<MemberOutcome> addMember(
            long userId, long teamId, AddMemberRequest request, UUID idempotencyKey) {
        String scope = "user:" + userId + ":POST:/api/v1/teams/" + teamId + "/members";
        String hash = fingerprint.compute("POST", "/api/v1/teams/" + teamId + "/members", request);
        return idempotencyService.execute(scope, idempotencyKey, hash,
                () -> doAddMember(userId, teamId, request),
                outcome -> new StoredResponse(201, outcome.rawJson()));
    }

    private MemberOutcome doAddMember(long userId, long teamId, AddMemberRequest request) {
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以添加成员");
        }
        String email = UserService.normalizeEmail(request.email());
        com.frameflow.identity.domain.User target = userMapper.findByEmail(email);
        if (target == null) {
            throw ApiException.notFound("目标用户不存在");
        }
        String role = request.role().name();
        TeamMember existing = memberMapper.findByTeamAndUserForUpdate(teamId, target.getId());
        com.frameflow.identity.web.dto.TeamMember dto;
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            throw ApiException.conflict(ErrorCodes.TEAM_MEMBER_ALREADY_EXISTS, "该用户已经是此团队的 ACTIVE 成员");
        }
        if (existing != null) {
            memberMapper.reactivateRemoved(teamId, target.getId(), role);
            dto = new com.frameflow.identity.web.dto.TeamMember(
                    existing.getId(), teamId, target.getId(), email, TeamRole.valueOf(role), MemberStatus.ACTIVE);
        } else {
            TeamMember member = new TeamMember();
            member.setTeamId(teamId);
            member.setUserId(target.getId());
            member.setRole(role);
            member.setStatus("ACTIVE");
            memberMapper.insert(member);
            dto = new com.frameflow.identity.web.dto.TeamMember(
                    member.getId(), teamId, target.getId(), email, TeamRole.valueOf(role), MemberStatus.ACTIVE);
        }
        return new MemberOutcome(dto, json(dto));
    }

    @Transactional
    public com.frameflow.identity.web.dto.TeamMember updateRole(
            long userId, long teamId, long targetUserId, UpdateMemberRoleRequest request) {
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以修改成员角色");
        }
        TeamMember target = memberMapper.findActiveByTeamAndUser(teamId, targetUserId);
        if (target == null) {
            throw ApiException.notFound("成员不存在或无权访问");
        }
        String newRole = request.role().name();
        if ("OWNER".equals(target.getRole()) && !"OWNER".equals(newRole)) {
            memberMapper.lockMembersByTeam(teamId);
            if (memberMapper.countActiveOwners(teamId) <= 1) {
                throw ApiException.conflict(ErrorCodes.TEAM_LAST_OWNER_CONFLICT, "团队必须至少保留一名 ACTIVE OWNER");
            }
        }
        memberMapper.updateRole(target.getId(), newRole);
        com.frameflow.identity.domain.User targetUser = userMapper.findById(targetUserId);
        return new com.frameflow.identity.web.dto.TeamMember(
                target.getId(), teamId, targetUserId, targetUser.getEmail(),
                TeamRole.valueOf(newRole), MemberStatus.ACTIVE);
    }

    @Transactional
    public void removeMember(long userId, long teamId, long targetUserId) {
        TeamMember caller = requireActiveMembership(userId, teamId);
        if (!"OWNER".equals(caller.getRole())) {
            throw ApiException.forbidden("仅团队 OWNER 可以移除成员");
        }
        if (userId == targetUserId) {
            throw ApiException.conflict(ErrorCodes.TEAM_SELF_REMOVAL_FORBIDDEN, "OWNER 不能通过成员删除接口移除自己");
        }
        TeamMember target = memberMapper.findActiveByTeamAndUser(teamId, targetUserId);
        if (target == null) {
            throw ApiException.notFound("成员不存在或无权访问");
        }
        memberMapper.lockMembersByTeam(teamId);
        if ("OWNER".equals(target.getRole()) && memberMapper.countActiveOwners(teamId) <= 1) {
            throw ApiException.conflict(ErrorCodes.TEAM_LAST_OWNER_CONFLICT, "团队必须至少保留一名 ACTIVE OWNER");
        }
        memberMapper.updateStatus(target.getId(), "REMOVED");
    }

    private TeamMember requireActiveMembership(long userId, long teamId) {
        TeamMember member = memberMapper.findActiveByTeamAndUser(teamId, userId);
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
