package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateTeamRequest")
public record CreateTeamRequest(
        @Schema(description = "团队名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 1, max = 100) String name) {
}
