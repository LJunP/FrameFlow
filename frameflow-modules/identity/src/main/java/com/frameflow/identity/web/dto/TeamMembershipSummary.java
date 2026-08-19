package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "TeamMembershipSummary")
public record TeamMembershipSummary(
        @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED) long teamId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberStatus status,
        @Schema(type = "string", format = "date-time", description = "团队创建时间",
                requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt) {
}
