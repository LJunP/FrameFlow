package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AddMemberRequest")
public record AddMemberRequest(
        @Schema(description = "已注册目标用户邮箱；M01 直接创建或恢复 ACTIVE 成员关系，不发送邀请",
                requiredMode = Schema.RequiredMode.REQUIRED, format = "email")
        @Email @NotNull String email,
        @Schema(description = "初始团队角色", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull TeamRole role) {

    /** 邮箱验证前先去除首尾空白。 */
    public AddMemberRequest {
        email = email == null ? null : email.trim();
    }
}
