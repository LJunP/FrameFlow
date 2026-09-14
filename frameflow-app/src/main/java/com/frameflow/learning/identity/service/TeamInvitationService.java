package com.frameflow.learning.identity.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.repo.InvitationMapper;
import com.frameflow.learning.identity.repo.InvitationRow;
import com.frameflow.learning.identity.repo.MemberMapper;
import com.frameflow.learning.identity.repo.RefreshTokenMapper;
import com.frameflow.learning.identity.repo.TeamMapper;
import com.frameflow.learning.identity.repo.UserMapper;
import com.frameflow.learning.identity.repo.UserRow;
import com.frameflow.learning.identity.security.TokenService;
import com.frameflow.learning.identity.web.AuthDtos.AcceptInvitationRequest;
import com.frameflow.learning.identity.web.AuthDtos.AuthResponse;
import com.frameflow.learning.identity.web.AuthDtos.CreateInvitationRequest;
import com.frameflow.learning.identity.web.AuthDtos.InvitationResponse;
import com.frameflow.learning.identity.web.AuthDtos.TeamResponse;
import com.frameflow.learning.identity.web.AuthDtos.UserResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团队成员邀请：Owner 发邀请（生成一次性令牌）→ 受邀人持链接接受（建档入团）。
 *
 * 与 AuthService 的关系：注册走"自建团队"路径（register），邀请走"加入已有团队"
 * 路径（本类）。两者都落 users + team_members，但入口校验与角色来源完全不同——
 * 邀请的角色由邀请单冻结，受邀人自己不能选。
 */
@Service
public class TeamInvitationService {

    /** 邀请链接有效期：7 天。过期后 Owner 需重新发起（令牌一次性，不设计"续期"）。 */
    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InvitationMapper invitations;
    private final UserMapper users;
    private final MemberMapper members;
    private final TeamMapper teams;
    private final RefreshTokenMapper refreshTokens;
    private final TeamAccessService teamAccess;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final MailService mail;
    private final SecureRandom secureRandom = new SecureRandom();

    public TeamInvitationService(InvitationMapper invitations, UserMapper users,
                                 MemberMapper members, TeamMapper teams,
                                 RefreshTokenMapper refreshTokens, TeamAccessService teamAccess,
                                 TokenService tokenService, PasswordEncoder passwordEncoder,
                                 Clock clock, MailService mail) {
        this.invitations = invitations;
        this.users = users;
        this.members = members;
        this.teams = teams;
        this.refreshTokens = refreshTokens;
        this.teamAccess = teamAccess;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.mail = mail;
    }

    /** 创建邀请：仅 OWNER（404→403 判定复用 TeamAccessService 的唯一路径）。 */
    @Transactional
    public InvitationResponse createInvite(long userId, long teamId, CreateInvitationRequest req) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        // 已在团里的人不需要邀请——提前给出可读错误，而不是等接受时撞主键
        UserRow existing = users.findByEmail(req.email());
        if (existing != null && members.findByUserAndTeam(existing.getId(), teamId) != null) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        String token = newInviteToken();
        // ★ 核心：库里只留哈希。明文令牌在这一次响应里返回一次就再无副本——
        // Owner 必须当场保存，之后连 Owner 自己都拉不回来（列表接口不返回令牌）。
        // 这样数据库泄露时攻击者拿到的是一堆无法还原的哈希，而不是可用的入团凭证。
        Long id = invitations.insert(teamId, req.email(), req.role(),
                tokenService.sha256(token), now.plus(INVITE_TTL), userId);
        String link = mail.publicBaseUrl() + "/invite/accept?token=" + token;
        mail.send(req.email(), "你被邀请加入 FrameFlow 团队",
                "角色：" + req.role() + "\n打开链接接受邀请（7 天内有效）：\n" + link);
        return new InvitationResponse(id, req.email(), req.role(), token,
                now.plus(INVITE_TTL), null, now, null);
    }

    /** 撤销未接受的邀请：仅 OWNER。已接受的邀请不能撤销（成员关系已成立）。 */
    @Transactional
    public void revokeInvite(long userId, long teamId, long invitationId) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        if (invitations.markRevoked(invitationId, teamId, OffsetDateTime.now(clock)) == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** 邀请记录列表：仅 OWNER（与成员列表同一条权限规则）。 */
    public List<InvitationResponse> listInvites(long userId, long teamId) {
        teamAccess.requireRole(userId, teamId, Role.OWNER);
        return invitations.listByTeam(teamId).stream().map(this::toResponse).toList();
    }

    /**
     * 接受邀请：核销令牌 + （必要时）建档 + 入团，一个事务；成功后直接签发
     * 双令牌，受邀人无需再走一次登录。
     */
    // ★ 核心：三个校验顺序固定——先验令牌（INVITATION_INVALID 统一话术防探测），
    // 再处理账号，最后入团。全在一个事务里：任何一步失败，邀请单保持
    // "未接受"状态，用户可以修正后重试，不会产生"半个账号"。
    @Transactional
    public AuthResponse acceptInvite(AcceptInvitationRequest req) {
        // 用哈希查库：请求带来的是明文令牌，库里存的是它的 SHA-256
        InvitationRow invite = invitations.findByTokenHash(tokenService.sha256(req.token()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (invite == null || invite.getAcceptedAt() != null
                || invite.getRevokedAt() != null || invite.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.INVITATION_INVALID);
        }

        UserRow user = users.findByEmail(invite.getEmail());
        long userId;
        String displayName;
        if (user != null) {
            // ★ 核心：受邀邮箱已有账号时必须验原密码——否则任何拿到链接的人
            // 都能顶替该账号入团并领走它的双令牌，等于账号接管。
            // 改坏后果：去掉这个分支，邀请链接从"入团凭证"升级为"盗号凭证"。
            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
            }
            userId = user.getId();
            displayName = user.getDisplayName();
        } else {
            displayName = (req.displayName() == null || req.displayName().isBlank())
                    ? invite.getEmail().split("@")[0]
                    : req.displayName();
            try {
                userId = users.insert(invite.getEmail(),
                        passwordEncoder.encode(req.password()),   // 只存哈希，与注册一致
                        displayName);
            } catch (DuplicateKeyException e) {
                // 并发下"查无此人 → 撞 UNIQUE"的缝隙，与 AuthService.register 同处理
                throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }

        if (members.findByUserAndTeam(userId, invite.getTeamId()) != null) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        try {
            // 角色来自邀请单而非请求体——受邀人无法给自己提权
            members.insert(invite.getTeamId(), userId, invite.getRole());
        } catch (DuplicateKeyException e) {
            // 复合主键 (team_id, user_id) 是并发下的最后防线
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        invitations.markAccepted(invite.getId(), now);

        String teamName = teams.findById(invite.getTeamId()).getName();
        return issueAuth(userId, invite.getEmail(), displayName,
                invite.getTeamId(), teamName, invite.getRole());
    }

    /** 与 AuthService.issueAuthResponse 同构：access JWT + 可吊销 refresh。 */
    private AuthResponse issueAuth(long userId, String email, String displayName,
                                   long teamId, String teamName, String role) {
        String accessToken = tokenService.issueAccessToken(userId, email, teamId, role);
        String refreshToken = tokenService.issueRefreshToken();
        refreshTokens.insert(userId, tokenService.sha256(refreshToken),
                tokenService.refreshTokenExpiry());
        return new AuthResponse(
                new UserResponse(userId, email, displayName, users.findById(userId).getEmailVerifiedAt() != null),
                new TeamResponse(teamId, teamName, role),
                accessToken, refreshToken);
    }

    // ★ 核心：邀请令牌 = 32 字节 SecureRandom 的十六进制（256bit 熵）。
    // 邀请链接本阶段手工转发、本身就是入团凭证，必须不可枚举；与 refresh
    // token 同级的随机强度。64 位 hex 正好落进 CHAR(64)。
    private String newInviteToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private InvitationResponse toResponse(InvitationRow row) {
        // 列表路径的 SQL 不查 token_hash，这里必然是 null——有意为之：
        // 列表只需要"发给谁、什么角色、是否已接受、是否过期"，不需要凭证本身。
        return new InvitationResponse(row.getId(), row.getEmail(), row.getRole(), null,
                row.getExpiresAt(), row.getAcceptedAt(), row.getCreatedAt(), row.getRevokedAt());
    }
}
