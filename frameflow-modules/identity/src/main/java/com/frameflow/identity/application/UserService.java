package com.frameflow.identity.application;

import com.frameflow.identity.application.model.AuthModels.LoginCommand;
import com.frameflow.identity.application.model.AuthModels.RegisterCommand;
import com.frameflow.identity.application.model.AuthModels.TokenPairView;
import com.frameflow.identity.application.model.AuthModels.UserView;
import com.frameflow.identity.application.port.out.UserRepository;
import com.frameflow.identity.domain.User;
import com.frameflow.identity.error.ApiException;
import com.frameflow.identity.error.ErrorCodes;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login and current-user lookup. Passwords are BCrypt-hashed only. */
@Service
@ConditionalOnWebApplication
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final ActiveUserPolicy activeUsers;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService, RefreshTokenService refreshTokenService,
                       ActiveUserPolicy activeUsers) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.activeUsers = activeUsers;
    }

    @Transactional
    public UserView register(RegisterCommand request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email) != null) {
            throw ApiException.conflict(ErrorCodes.USER_EMAIL_CONFLICT, "该邮箱已被注册");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus("ACTIVE");
        try {
            users.insert(user);
        } catch (DataIntegrityViolationException ex) {
            // The pre-check is only an optimization. PostgreSQL's unique constraint is
            // the fact source when two registrations race.
            throw ApiException.conflict(ErrorCodes.USER_EMAIL_CONFLICT, "该邮箱已被注册");
        }
        return toView(user);
    }

    @Transactional
    public TokenPairView login(LoginCommand request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())
                || !"ACTIVE".equals(user.getStatus())) {
            throw ApiException.unauthenticated("邮箱或密码错误");
        }
        users.touchLastLogin(user.getId());
        String accessToken = accessTokenService.issue(user.getId());
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(user.getId());
        return new TokenPairView(accessToken, issued.refreshToken(), accessTokenService.expiresInSeconds());
    }

    @Transactional(readOnly = true)
    public UserView me(long userId) {
        return toView(activeUsers.requireActive(userId));
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getStatus(), user.getLastLoginAt());
    }
}
