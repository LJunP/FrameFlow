package com.frameflow.learning.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.frameflow.learning.identity.repo.PasswordResetMapper;
import com.frameflow.learning.identity.repo.PasswordResetRow;
import com.frameflow.learning.identity.repo.RefreshTokenMapper;
import com.frameflow.learning.identity.repo.UserMapper;
import com.frameflow.learning.identity.repo.UserRow;
import com.frameflow.learning.identity.security.TokenService;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import com.frameflow.learning.shared.ratelimit.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自助找回密码。公开接口始终 204，避免用响应差异枚举邮箱。
 *
 * 本阶段不接入 SMTP：明文令牌只在 local 日志出现一次，生产必须另接邮件通道
 * 才能把链接交给用户。工程闭环（哈希存储 / 一次性核销 / 吊销会话）与邀请令牌同级。
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserMapper users;
    private final PasswordResetMapper tokens;
    private final RefreshTokenMapper refreshTokens;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final RedisRateLimiter rateLimiter;
    private final Clock clock;
    private final String environment;
    private final MailService mail;

    public PasswordResetService(UserMapper users, PasswordResetMapper tokens,
                                RefreshTokenMapper refreshTokens, TokenService tokenService,
                                PasswordEncoder passwordEncoder, RedisRateLimiter rateLimiter,
                                Clock clock, @Value("${frameflow.env:local}") String environment,
                                MailService mail) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.environment = environment;
        this.mail = mail;
    }

    /**
     * 申请重置。返回值仅供测试与 local 日志使用，HTTP 层必须丢弃，
     * 否则"有没有这个邮箱"会从响应体里漏出去。
     */
    // ★ 核心：未知邮箱与已知邮箱走同一条限流、同一条 204。
    // 若未知邮箱提前 return、已知邮箱才写库，攻击者仍可用时序/日志差异枚举。
    // 改坏后果：找回密码接口变成用户目录。
    @Transactional
    public Optional<String> requestReset(String email) {
        if (!rateLimiter.tryAcquire("ratelimit:pwreset:" + email, 5, 1.0 / 30)) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        UserRow user = users.findByEmail(email);
        if (user == null) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        tokens.invalidateOpen(user.getId(), now);
        String raw = tokenService.issueRefreshToken();
        tokens.insert(user.getId(), tokenService.sha256(raw), now.plus(RESET_TTL));
        String link = mail.publicBaseUrl() + "/reset-password?token=" + raw;
        mail.send(user.getEmail(), "重置 FrameFlow 密码",
                "打开此链接设置新密码（1 小时内有效）：\n" + link);
        if ("local".equalsIgnoreCase(environment)) {
            log.info("password-reset token issued userId={} token={} (also mailed if SMTP configured)",
                    user.getId(), raw);
        }
        return Optional.of(raw);
    }

    /**
     * 凭令牌设置新密码。成功后吊销全部 refresh——否则知道旧密码的会话仍能续期。
     */
    // ★ 核心：无效/过期/已用统一 PASSWORD_RESET_INVALID，不区分细节。
    // 条件更新 markUsed 是并发核销点：两个请求同时确认，只有一行能把 used_at
    // 从 NULL 写成现在；输家视为令牌已失效。改坏后果：同一链接改两次密码。
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PasswordResetRow row = tokens.findByTokenHash(tokenService.sha256(rawToken));
        if (row == null || row.getUsedAt() != null || row.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_INVALID);
        }
        UserRow user = users.findById(row.getUserId());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "新密码不能与原密码相同");
        }
        if (tokens.markUsed(row.getId(), now) == 0) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_INVALID);
        }
        users.updatePasswordHash(user.getId(), passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.getId(), now);
    }
}
