package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "Team")
public record Team(
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(type = "string", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt) {
}
