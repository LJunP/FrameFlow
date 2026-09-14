package com.frameflow.learning.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.frameflow.learning.identity.repo.EmailVerificationMapper;
import com.frameflow.learning.identity.repo.PasswordResetRow;
import com.frameflow.learning.identity.repo.UserMapper;
import com.frameflow.learning.identity.repo.UserRow;
import com.frameflow.learning.identity.security.TokenService;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final Duration TTL = Duration.ofHours(24);

    private final UserMapper users;
    private final EmailVerificationMapper tokens;
    private final TokenService tokenService;
    private final MailService mail;
    private final Clock clock;

    public EmailVerificationService(UserMapper users, EmailVerificationMapper tokens,
                                    TokenService tokenService, MailService mail, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> requestForUser(long userId) {
        UserRow user = users.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (user.getEmailVerifiedAt() != null) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        tokens.invalidateOpen(userId, now);
        String raw = tokenService.issueRefreshToken();
        tokens.insert(userId, tokenService.sha256(raw), now.plus(TTL));
        String link = mail.publicBaseUrl() + "/verify-email?token=" + raw;
        mail.send(user.getEmail(), "验证你的 FrameFlow 邮箱",
                "打开此链接完成邮箱验证（24 小时内有效）：\n" + link);
        return Optional.of(raw);
    }

    @Transactional
    public void confirm(String rawToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PasswordResetRow row = tokens.findByTokenHash(tokenService.sha256(rawToken));
        if (row == null || row.getUsedAt() != null || row.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_INVALID, "验证链接无效、已被使用或已过期");
        }
        if (tokens.markUsed(row.getId(), now) == 0) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_INVALID, "验证链接无效、已被使用或已过期");
        }
        users.markEmailVerified(row.getUserId(), now);
    }
}
