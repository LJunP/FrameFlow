package com.frameflow.learning.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * identity 接口的请求/响应 DTO（Java 17 record + Bean Validation）。
 * 校验注解在入口处挡住非法输入，Service 拿到的永远是合法数据。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 64) String teamName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record UserResponse(Long id, String email, String displayName) {
    }

    public record TeamResponse(Long id, String name, String role) {
    }

    /** 注册/登录/刷新共用的成功响应：用户 + 所属团队 + 双令牌。 */
    public record AuthResponse(UserResponse user, TeamResponse team,
                               String accessToken, String refreshToken) {
    }

    public record MemberResponse(Long userId, String email, String displayName, String role) {
    }
}
