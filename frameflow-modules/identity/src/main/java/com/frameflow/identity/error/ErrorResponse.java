package com.frameflow.identity.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Error")
public record ErrorResponse(
        @Schema(description = "稳定错误码", example = "AUTH_REQUIRED",
                requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "人类可读的错误说明", requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(description = "必须与响应 X-Request-Id header 完全相同", format = "uuid",
                requiredMode = Schema.RequiredMode.REQUIRED) String requestId,
        @Schema(description = "可观测环境可选") String traceId) {
}
