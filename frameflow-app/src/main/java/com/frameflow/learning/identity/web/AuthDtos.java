package com.frameflow.learning.identity.web;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 1, max = 64) String displayName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 255) String email) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(min = 16, max = 128) String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record UserResponse(Long id, String email, String displayName, Boolean emailVerified) {
    }

    public record SwitchTeamRequest(@jakarta.validation.constraints.NotNull Long teamId) {
    }

    public record TransferOwnerRequest(@jakarta.validation.constraints.NotNull Long userId) {
    }

    public record VerifyEmailConfirmRequest(
            @NotBlank @Size(min = 16, max = 128) String token) {
    }

    public record TeamResponse(Long id, String name, String role) {
    }

    /** 注册/登录/刷新共用的成功响应：用户 + 所属团队 + 双令牌。 */
    public record AuthResponse(UserResponse user, TeamResponse team,
                               String accessToken, String refreshToken) {
    }

    public record MemberResponse(Long userId, String email, String displayName, String role) {
    }

    public record CreateInvitationRequest(
            @NotBlank @Email @Size(max = 255) String email,
            // ★ 核心：邀请角色白名单不含 OWNER（应用层防线），与 V7 迁移的
            // CHECK 约束共同保证"Owner 只能由注册路径创建"这条不变量。
            @NotBlank @Pattern(regexp = "OPERATOR|REVIEWER|VIEWER") String role) {
    }

    public record AcceptInvitationRequest(
            @NotBlank @Size(min = 64, max = 64) String token,
            // 受邀邮箱已有账号时，此字段用于验证"确是本人"（见 TeamInvitationService）
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 64) String displayName) {
    }

    /**
     * 邀请视图。token 只在创建响应里出现一次（明文）；列表接口恒为 null。
     * revokedAt 非空表示 Owner 已作废，接受接口会按 INVITATION_INVALID 拒绝。
     */
    public record InvitationResponse(Long id, String email, String role, String token,
                                     OffsetDateTime expiresAt, OffsetDateTime acceptedAt,
                                     OffsetDateTime createdAt, OffsetDateTime revokedAt) {
    }

    /**
     * 角色变更请求。★ 核心：@Pattern 直接排除 OWNER——"把某人提为 OWNER"在
     * 本接口语义上不存在（OWNER 只能由注册产生），若只校验"四个枚举值之一"
     * 就会留出一个提权后门。非法值在进 Service 前已被 400 拦截。
     */
    public record UpdateMemberRoleRequest(
            @NotBlank @Pattern(regexp = "OPERATOR|REVIEWER|VIEWER") String role) {
    }
}
