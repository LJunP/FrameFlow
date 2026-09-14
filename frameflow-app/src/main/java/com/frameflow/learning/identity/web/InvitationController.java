package com.frameflow.learning.identity.web;

import java.util.List;

import com.frameflow.learning.identity.service.TeamInvitationService;
import com.frameflow.learning.identity.web.AuthDtos.AcceptInvitationRequest;
import com.frameflow.learning.identity.web.AuthDtos.AuthResponse;
import com.frameflow.learning.identity.web.AuthDtos.CreateInvitationRequest;
import com.frameflow.learning.identity.web.AuthDtos.InvitationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队邀请接口。权限分两层：
 * - 创建/列表挂在 /api/v1/teams/{teamId}/invitations 下，需要 Bearer token，
 *   Service 内由 TeamAccessService 做 404→403 的 OWNER 判定；
 * - 接受接口 /api/v1/invitations/accept 在 SecurityConfig 里显式 permitAll——
 *   受邀人此刻还没有会话（甚至可能没有账号），唯一凭证是 64 位随机令牌本身。
 */
@RestController
public class InvitationController {

    private final TeamInvitationService invitationService;

    public InvitationController(TeamInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/v1/teams/{teamId}/invitations")
    public InvitationResponse create(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable long teamId,
                                     @Valid @RequestBody CreateInvitationRequest req) {
        return invitationService.createInvite(Long.parseLong(jwt.getSubject()), teamId, req);
    }

    @GetMapping("/api/v1/teams/{teamId}/invitations")
    public List<InvitationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long teamId) {
        return invitationService.listInvites(Long.parseLong(jwt.getSubject()), teamId);
    }

    @DeleteMapping("/api/v1/teams/{teamId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable long teamId,
                       @PathVariable long invitationId) {
        invitationService.revokeInvite(Long.parseLong(jwt.getSubject()), teamId, invitationId);
    }

    @PostMapping("/api/v1/invitations/accept")
    public AuthResponse accept(@Valid @RequestBody AcceptInvitationRequest req) {
        return invitationService.acceptInvite(req);
    }
}
