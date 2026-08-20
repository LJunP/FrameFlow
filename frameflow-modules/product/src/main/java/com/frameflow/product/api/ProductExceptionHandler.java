package com.frameflow.product.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.frameflow.product.web")
public class ProductExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), ex.getStatus(), ex.getCode(),
                ex.getMessage(), requestId);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        log.error("product internal error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), 500, "INTERNAL_ERROR",
                "internal error", requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}