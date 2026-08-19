package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest")
public record RegisterRequest(
        @Schema(description = "邮箱（小写规范化）", requiredMode = Schema.RequiredMode.REQUIRED, format = "email")
        @Email @NotNull @Size(max = 255) String email,
        @Schema(description = "密码（BCrypt 存储，永不出现在响应）", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotNull @Size(min = 8, max = 72) String password,
        @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 1, max = 100) String displayName) {

    /** 邮箱验证前先去除首尾空白，避免 " user@example.com " 这类输入误报格式错误。 */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
