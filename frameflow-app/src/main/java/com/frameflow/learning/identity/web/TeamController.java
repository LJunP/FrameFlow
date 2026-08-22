package com.frameflow.learning.identity.web;

import java.util.List;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.MemberResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队成员查询：完整演示 404/403 两层防线（判定逻辑在 AuthService.teamMembers，
 * 含 ★ 注释——为什么 404 必须在 403 之前）。
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/members")
public class TeamController {

    private final AuthService authService;

    public TeamController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable long teamId) {
        return authService.teamMembers(Long.parseLong(jwt.getSubject()), teamId);
    }
}
