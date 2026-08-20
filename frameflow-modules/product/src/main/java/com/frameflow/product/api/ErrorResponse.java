package com.frameflow.product.api;

import java.time.OffsetDateTime;

public record ErrorResponse(OffsetDateTime timestamp, int status, String code, String message, String requestId) {
}
