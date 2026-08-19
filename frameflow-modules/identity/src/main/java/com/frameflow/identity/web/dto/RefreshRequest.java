package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RefreshRequest")
public record RefreshRequest(
        @Schema(description = "32 字节安全随机值的 Base64URL 无 padding 编码",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 43, maxLength = 43)
        @NotBlank @Size(min = 43, max = 43) String refreshToken) {
}
