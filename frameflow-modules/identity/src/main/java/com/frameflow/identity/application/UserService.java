package com.frameflow.identity.application;

import com.frameflow.identity.domain.User;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.infrastructure.persistence.UserMapper;
import com.frameflow.identity.web.dto.LoginRequest;
import com.frameflow.identity.web.dto.RegisterRequest;
import com.frameflow.identity.web.dto.TokenPair;
import com.frameflow.identity.web.dto.TokenType;
import com.frameflow.identity.web.dto.UserStatus;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login and current-user lookup. Passwords are BCrypt-hashed only. */
@Service
@ConditionalOnWebApplication
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public com.frameflow.identity.web.dto.User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userMapper.findByEmail(email) != null) {
            throw ApiException.conflict("USER_EMAIL_CONFLICT", "该邮箱已被注册");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return toDto(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userMapper.findByEmail(email);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())
                || !"ACTIVE".equals(user.getStatus())) {
            throw ApiException.unauthenticated("邮箱或密码错误");
        }
        userMapper.touchLastLogin(user.getId());
        String accessToken = accessTokenService.issue(user.getId());
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(user.getId());
        return new TokenPair(accessToken, issued.refreshToken(), accessTokenService.expiresInSeconds(), TokenType.Bearer);
    }

    @Transactional(readOnly = true)
    public com.frameflow.identity.web.dto.User me(long userId) {
        User user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw ApiException.unauthenticated("用户不存在或已禁用");
        }
        return toDto(user);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static com.frameflow.identity.web.dto.User toDto(User user) {
        return new com.frameflow.identity.web.dto.User(
                user.getId(), user.getEmail(), user.getDisplayName(),
                UserStatus.valueOf(user.getStatus()), user.getLastLoginAt());
    }
}
