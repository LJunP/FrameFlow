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

    public AuthService(UserMapper users, TeamMapper teams, MemberMapper members,
                       RefreshTokenMapper refreshTokens, TokenService tokenService,
                       PasswordEncoder passwordEncoder, Clock clock,
                       TeamAccessService teamAccess) {
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.teamAccess = teamAccess;
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
            return issueAuthResponse(userId, req.email(), req.displayName(), teamId, teamName, Role.OWNER.name());
        } catch (DuplicateKeyException e) {
            // ★ 核心：应用层查重在并发下有缝隙（两个请求同时查到"邮箱不存在"
            // 然后都插入）——数据库的 UNIQUE 约束是最后防线，冲突会抛
            // DuplicateKeyException，在这里翻译回业务错误。两层防线缺一不可。
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public AuthResponse login(LoginRequest req) {
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

    public AuthResponse me(long userId) {
        UserRow user = users.findById(userId);
        MemberRow member = members.findFirstByUser(userId);
        return new AuthResponse(
                new UserResponse(user.getId(), user.getEmail(), user.getDisplayName()),
                new TeamResponse(member.getTeamId(), teamNameOf(member), member.getRole()),
                null, null);
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

    private AuthResponse issueAuthResponse(long userId, String email, String displayName,
                                           long teamId, String teamName, String role) {
        String accessToken = tokenService.issueAccessToken(userId, email, teamId, role);
        String refreshToken = tokenService.issueRefreshToken();
        refreshTokens.insert(userId, tokenService.sha256(refreshToken),
                tokenService.refreshTokenExpiry());
        return new AuthResponse(
                new UserResponse(userId, email, displayName),
                new TeamResponse(teamId, teamName, role),
                accessToken, refreshToken);
    }

    private String teamNameOf(MemberRow member) {
        return teams.findById(member.getTeamId()).getName();
    }

    private MemberResponse toMemberResponse(TeamMemberView view) {
        return new MemberResponse(view.getUserId(), view.getEmail(),
                view.getDisplayName(), view.getRole());
    }
}
