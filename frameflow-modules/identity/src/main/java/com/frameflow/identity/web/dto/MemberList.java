package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "MemberList")
public record MemberList(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamMember> items) {
}
