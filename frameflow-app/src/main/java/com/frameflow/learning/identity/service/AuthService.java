package com.frameflow.learning.identity.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.repo.MemberMapper;
import com.frameflow.learning.identity.repo.MemberRow;
import com.frameflow.learning.identity.repo.RefreshTokenMapper;
import com.frameflow.learning.identity.repo.RefreshTokenRow;
import com.frameflow.learning.identity.repo.TeamMapper;
import com.frameflow.learning.identity.repo.TeamMemberView;
import com.frameflow.learning.identity.repo.UserMapper;
import com.frameflow.learning.identity.repo.UserRow;
import com.frameflow.learning.identity.security.TokenService;
import com.frameflow.learning.identity.web.AuthDtos.AuthResponse;
import com.frameflow.learning.identity.web.AuthDtos.LoginRequest;
import com.frameflow.learning.identity.web.AuthDtos.MemberResponse;
import com.frameflow.learning.identity.web.AuthDtos.RegisterRequest;
import com.frameflow.learning.identity.web.AuthDtos.TeamResponse;
import com.frameflow.learning.identity.web.AuthDtos.UserResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证/成员业务。F1 的核心业务类——注册、登录、刷新轮换、越权判定都在这。
 */
@Service
public class AuthService {

    private final UserMapper users;
    private final TeamMapper teams;
    private final MemberMapper members;
    private final RefreshTokenMapper refreshTokens;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TeamAccessService teamAccess;
    private final com.frameflow.learning.shared.ratelimit.RedisRateLimiter rateLimiter;
    private final EmailVerificationService emailVerification;

    public AuthService(UserMapper users, TeamMapper teams, MemberMapper members,
                       RefreshTokenMapper refreshTokens, TokenService tokenService,
                       PasswordEncoder passwordEncoder, Clock clock,
                       TeamAccessService teamAccess,
                       com.frameflow.learning.shared.ratelimit.RedisRateLimiter rateLimiter,
                       EmailVerificationService emailVerification) {
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.teamAccess = teamAccess;
        this.rateLimiter = rateLimiter;
        this.emailVerification = emailVerification;
    }

    /**
     * 注册：创建 团队 + 用户(OWNER) + 成员关系，一步到位，整体一个事务。
     */
    // ★ 核心：@Transactional 保证原子性——建了团队但建用户失败时，团队创建
    // 一起回滚，数据库不会留下"没有成员的幽灵团队"。三个 INSERT 要么全成
    // 要么全无，这是"事务边界"最典型的应用场景：一次业务动作 = 一个事务。
    // 改坏后果：去掉注解后，中途失败留下脏数据，下次注册同名团队直接报错。
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.findByEmail(req.email()) != null) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String teamName = (req.teamName() == null || req.teamName().isBlank())
                ? req.displayName() + "-team"
                : req.teamName();
        try {
            Long teamId = teams.insert(teamName);
            Long userId = users.insert(req.email(),
                    passwordEncoder.encode(req.password()),   // ★ 只存哈希，明文不落库
                    req.displayName());
            members.insert(teamId, userId, Role.OWNER.name());
            AuthResponse response = issueAuthResponse(userId, req.email(), req.displayName(),
                    teamId, teamName, Role.OWNER.name());
            emailVerification.requestForUser(userId);
            return response;
        } catch (DuplicateKeyException e) {
            // ★ 核心：应用层查重在并发下有缝隙（两个请求同时查到"邮箱不存在"
            // 然后都插入）——数据库的 UNIQUE 约束是最后防线，冲突会抛
            // DuplicateKeyException，在这里翻译回业务错误。两层防线缺一不可。
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public AuthResponse login(LoginRequest req) {
        // F5 限流：每邮箱容量 5、每 30 秒回填 1 个令牌——
        // 正常用户（偶发输错一两次）无感；持续爆破被平滑压制。
        if (!rateLimiter.tryAcquire("ratelimit:login:" + req.email(), 5, 1.0 / 30)) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        UserRow user = users.findByEmail(req.email());
        // ★ 核心：用户不存在与密码错误必须返回同一个错误（INVALID_CREDENTIALS）——
        // 如果分开提示"用户不存在"/"密码错误"，攻击者就能批量探测哪些邮箱
        // 注册过（用户枚举）。统一话术让两种情况不可区分。
        // passwordEncoder.matches 内部是常量时间比较 + 自动处理盐，永不要手写
        // hash 后用 equals 比（那是可被计时攻击利用的写法）。
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        MemberRow member = members.findFirstByUser(user.getId());
        return issueAuthResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                member.getTeamId(), teamNameOf(member), member.getRole());
    }

    /**
     * 刷新令牌：一次性轮换——旧 refresh token 立即作废，同时签发新的一对。
     */
    // ★ 核心：轮换(rotation)的意义——每次用过的 refresh token 马上吊销，
    // 同一个 token 第二次出现 = 有人在高风险环境重放了旧凭证（可能是 token
    // 被盗后攻击者与真用户各自刷新）。F1 直接拒绝旧 token；生产级强化是
    // "检测到重放 → 吊销该用户整个令牌族"，把盗用者一起踢下线。
    // 改坏后果：不轮换的话，refresh token 泄露 = 攻击者可与真用户无限共生。
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        RefreshTokenRow row = refreshTokens.findByTokenHash(tokenService.sha256(rawRefreshToken));
        if (row == null || row.getRevokedAt() != null || row.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        refreshTokens.markRevoked(row.getId(), now);
        UserRow user = users.findById(row.getUserId());
        MemberRow member = members.findFirstByUser(user.getId());
        return issueAuthResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                member.getTeamId(), teamNameOf(member), member.getRole());
    }

    /** 登出：吊销该用户全部刷新令牌（access token 自然过期，最长 15 分钟）。 */
    @Transactional
    public void logout(long userId) {
        refreshTokens.revokeAllForUser(userId, OffsetDateTime.now(clock));
    }

    /** 更新昵称：返回最新用户信息，前端据此同步会话内的显示名。 */
    @Transactional
    public UserResponse updateProfile(long userId, String displayName) {
        UserRow user = users.findById(userId);
        users.updateDisplayName(userId, displayName);
        return new UserResponse(user.getId(), user.getEmail(), displayName,
                user.getEmailVerifiedAt() != null);
    }

    /**
     * 修改密码：验旧 → 拒绝新旧相同 → 换哈希 → 吊销全部刷新令牌。
     */
    // ★ 核心：改密成功后必须吊销该用户所有 refresh token——密码泄露场景下
    // 攻击者手里可能正握着有效会话；不换发新凭证，"改密码"就只是心理安慰。
    // 前端收到 204 后应清空本地会话并引导重新登录（旧 access token 最多还
    // 能活 15 分钟，这是无状态 JWT 换即时性换来的取舍，与登出同一语义）。
    // 改坏后果：去掉 revokeAllForUser，被盗的 refresh token 在改密后仍可续期。
    @Transactional
    public void changePassword(long userId, String oldPassword, String newPassword) {
        UserRow user = users.findById(userId);
        // 已登录会话内改密，无枚举顾虑，可以给明确提示（与登录的统一话术不同）
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "原密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "新密码不能与原密码相同");
        }
        users.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId, OffsetDateTime.now(clock));
    }

    public AuthResponse me(long userId) {
        UserRow user = users.findById(userId);
        MemberRow member = members.findFirstByUser(userId);
        return new AuthResponse(
                toUser(user),
                new TeamResponse(member.getTeamId(), teamNameOf(member), member.getRole()),
                null, null);
    }

    public List<TeamResponse> listTeams(long userId) {
        return members.listByUser(userId).stream()
                .map(row -> new TeamResponse(row.getTeamId(), teamNameOf(row), row.getRole()))
                .toList();
    }

    @Transactional
    public AuthResponse switchTeam(long userId, long teamId) {
        MemberRow member = teamAccess.requireMember(userId, teamId);
        UserRow user = users.findById(userId);
        return issueAuthResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                member.getTeamId(), teamNameOf(member), member.getRole());
    }

    @Transactional
    public void transferOwnership(long actorId, long teamId, long newOwnerId) {
        teamAccess.requireRole(actorId, teamId, Role.OWNER);
        if (actorId == newOwnerId) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "不能把所有权转给自己");
        }
        teamAccess.requireMember(newOwnerId, teamId);
        members.updateRole(teamId, newOwnerId, Role.OWNER.name());
        members.updateRole(teamId, actorId, Role.OPERATOR.name());
    }

    /**
     * 团队成员列表：404（不在团队）→ 403（在团队但非 OWNER）→ 200。
     * 判定逻辑已抽到 TeamAccessService（F2 起全项目复用同一规则），
     * 404-先-403 的防枚举语义见那里的 ★ 注释。
     */
    public List<MemberResponse> teamMembers(long userId, long teamId) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        return members.listByTeam(teamId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * 变更成员角色（仅 OWNER）。目标角色只能是 OPERATOR/REVIEWER/VIEWER——
     * 合法性由 UpdateMemberRoleRequest 的 @Pattern 在入口处保证（见该 DTO 的
     * ★ 注释），这里 valueOf 是安全解析。
     */
    // ★ 核心：两条"不可动"规则必须在写库前判定——
    // 1) 不能改自己的角色：OWNER 把自己降级会让团队陷入"无人可管"状态，
    //    权限体系里"剥夺自己的最高权限"必须由另一个同等权限者完成；
    // 2) 不能改 OWNER 的角色：OWNER 是团队的根权限，本接口不产生也不
    //    消灭 OWNER（角色转移若要支持，须单独设计带审计的交接流程）。
    // 改坏后果：漏掉任何一条，OWNER 都可能把自己锁死或被别人静默夺权。
    @Transactional
    public MemberResponse updateMemberRole(long userId, long teamId, long targetUserId, String newRole) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        MemberRow target = requireMutableTarget(userId, teamId, targetUserId);
        members.updateRole(teamId, targetUserId, Role.valueOf(newRole).name());
        UserRow user = users.findById(targetUserId);
        return new MemberResponse(user.getId(), user.getEmail(), user.getDisplayName(), newRole);
    }

    /**
     * 移除成员（仅 OWNER）：只删除 team_members 成员关系，不删 users 记录——
     * 用户账号是全局身份，被移出团队后仍应能登录（只是失去该团队上下文）。
     */
    // ★ 核心：与 updateMemberRole 共用同一套"不可动"判定（自己/OWNER），
    // 移除操作不可逆且立即切断访问权限，判错方向（漏判）没有挽回余地。
    @Transactional
    public void removeMember(long userId, long teamId, long targetUserId) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        requireMutableTarget(userId, teamId, targetUserId);
        members.delete(teamId, targetUserId);
    }

    /**
     * "可变更/可移除的目标成员"统一判定：
     * 自己 → 403；目标不在团队 → 404（不泄露成员关系存在性）；目标是 OWNER → 403。
     */
    private MemberRow requireMutableTarget(long userId, long teamId, long targetUserId) {
        if (userId == targetUserId) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        MemberRow target = members.findByUserAndTeam(targetUserId, teamId);
        if (target == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (Role.OWNER.name().equals(target.getRole())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return target;
    }

    private AuthResponse issueAuthResponse(long userId, String email, String displayName,
                                           long teamId, String teamName, String role) {
        String accessToken = tokenService.issueAccessToken(userId, email, teamId, role);
        String refreshToken = tokenService.issueRefreshToken();
        refreshTokens.insert(userId, tokenService.sha256(refreshToken),
                tokenService.refreshTokenExpiry());
        UserRow user = users.findById(userId);
        return new AuthResponse(
                toUser(user),
                new TeamResponse(teamId, teamName, role),
                accessToken, refreshToken);
    }

    private static UserResponse toUser(UserRow user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getEmailVerifiedAt() != null);
    }

    private String teamNameOf(MemberRow member) {
        return teams.findById(member.getTeamId()).getName();
    }

    private MemberResponse toMemberResponse(TeamMemberView view) {
        return new MemberResponse(view.getUserId(), view.getEmail(),
                view.getDisplayName(), view.getRole());
    }
}
