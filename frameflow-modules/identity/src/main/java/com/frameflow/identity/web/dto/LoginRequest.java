package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @Schema(description = "邮箱（登录前先去除首尾空白并转小写）", requiredMode = Schema.RequiredMode.REQUIRED, format = "email")
        @Email @NotNull String email,
        @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotNull String password) {

    /** 与 UserService.normalizeEmail 一致：验证前先去除首尾空白（大小写由服务层归一化）。 */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
