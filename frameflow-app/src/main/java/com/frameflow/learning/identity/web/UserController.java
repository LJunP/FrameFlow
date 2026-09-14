package com.frameflow.learning.identity.web;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.ChangePasswordRequest;
import com.frameflow.learning.identity.web.AuthDtos.UpdateProfileRequest;
import com.frameflow.learning.identity.web.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的资料与密码维护。两个接口都要求 Bearer token——
 * SecurityConfig 的"默认拒绝"会自动把它们纳入认证范围，无需显式声明。
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /** 改昵称：返回最新用户对象，前端直接同步进会话。 */
    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(Long.parseLong(jwt.getSubject()), req.displayName());
    }

    /** 改密码：成功后服务端吊销全部刷新令牌，客户端需重新登录，故返回 204。 */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(Long.parseLong(jwt.getSubject()), req.oldPassword(), req.newPassword());
    }
}
