package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateMemberRoleRequest")
public record UpdateMemberRoleRequest(
        @Schema(description = "新团队角色", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull TeamRole role) {
}
