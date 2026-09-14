package com.frameflow.learning.identity.web;

import java.util.List;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.MemberResponse;
import com.frameflow.learning.identity.web.AuthDtos.UpdateMemberRoleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队成员查询与管理：完整演示 404/403 两层防线（判定逻辑在 AuthService，
 * 含 ★ 注释——为什么 404 必须在 403 之前；角色/移除的两条"不可动"规则
 * 见 AuthService.updateMemberRole/removeMember）。
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

    /** 变更成员角色：200 返回变更后的成员视图，供前端直接刷新该行。 */
    @PutMapping("/{userId}/role")
    public MemberResponse updateRole(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable long teamId,
                                     @PathVariable long userId,
                                     @Valid @RequestBody UpdateMemberRoleRequest req) {
        return authService.updateMemberRole(
                Long.parseLong(jwt.getSubject()), teamId, userId, req.role());
    }

    /** 移除成员：204 无体——幂等删除语义（重复删同样"不存在"，由 404 表达）。 */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable long teamId,
                       @PathVariable long userId) {
        authService.removeMember(Long.parseLong(jwt.getSubject()), teamId, userId);
    }
}
