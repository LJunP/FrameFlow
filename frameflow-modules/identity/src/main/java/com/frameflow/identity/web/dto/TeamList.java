package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "TeamList")
public record TeamList(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamMembershipSummary> items) {
}
