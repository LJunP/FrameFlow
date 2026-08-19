package com.frameflow.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenPair")
public record TokenPair(
        @Schema(description = "RS256 JWT，TTL 15 分钟", requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(description = "32 字节安全随机的不透明 Base64URL 字符串；轮换且 TTL 30 天",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 43, maxLength = 43) String refreshToken,
        @Schema(description = "Access Token 有效期（秒）", example = "900",
                requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TokenType tokenType) {
}
