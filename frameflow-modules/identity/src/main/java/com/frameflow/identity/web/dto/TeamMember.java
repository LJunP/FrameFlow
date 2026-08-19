package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TeamMember")
public record TeamMember(
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long teamId,
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long userId,
        @Schema(type = "string", format = "email", requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberStatus status) {
}
