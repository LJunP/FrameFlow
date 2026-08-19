package com.frameflow.identity.error;

import com.frameflow.identity.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/** Maps exceptions to the unified error body; requestId always equals the X-Request-Id header. */
@RestControllerAdvice(basePackages = "com.frameflow.identity.web")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getHttpStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError fe = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String msg = fe == null ? "参数校验失败" : (fe.getField() + " " + fe.getDefaultMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, msg, request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数无效", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, "资源不存在或无权访问", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "服务器内部错误", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code, message, RequestIdFilter.requestId(request), null);
        return ResponseEntity.status(status).body(body);
    }
}
