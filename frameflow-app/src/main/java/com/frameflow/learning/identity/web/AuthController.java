package com.frameflow.learning.identity.web;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.AuthResponse;
import com.frameflow.learning.identity.web.AuthDtos.LoginRequest;
import com.frameflow.learning.identity.web.AuthDtos.RefreshRequest;
import com.frameflow.learning.identity.web.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出。
 * 前三个在 SecurityConfig 里显式 permitAll，登出需要 Bearer token。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册需要 Idempotency-Key 请求头（由 IdempotencyFilter 强制并做重放）。 */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(Long.parseLong(jwt.getSubject()));
    }
}
