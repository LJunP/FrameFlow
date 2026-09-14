package com.frameflow.learning.identity.web;

import java.util.List;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.AuthResponse;
import com.frameflow.learning.identity.web.AuthDtos.SwitchTeamRequest;
import com.frameflow.learning.identity.web.AuthDtos.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户信息。这是 F1 的"受保护资源示例"：不带 token 访问会被
 * SecurityConfig 拦下返回 401（见那里的 authenticationEntryPoint）。
 */
@RestController
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    // ★ 核心：@AuthenticationPrincipal Jwt——验签通过后，Spring Security 把
    // 解析好的 Jwt 放进方法参数。注意信任边界：这里的 claims 已经过签名
    // 和有效期校验，可以放心使用；Controller 不需要（也不应该）再验一次。
    @GetMapping("/api/v1/me")
    public AuthResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/api/v1/me/teams")
    public List<TeamResponse> teams(@AuthenticationPrincipal Jwt jwt) {
        return authService.listTeams(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/api/v1/me/current-team")
    public AuthResponse switchTeam(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody SwitchTeamRequest req) {
        return authService.switchTeam(Long.parseLong(jwt.getSubject()), req.teamId());
    }
}
