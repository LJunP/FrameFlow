package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "User")
public record User(
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(type = "string", format = "email", requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserStatus status,
        @Schema(type = "string", format = "date-time", nullable = true) OffsetDateTime lastLoginAt) {
}
