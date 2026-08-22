package com.frameflow.learning.shared.error;

/**
 * 业务异常：Service 层抛出，由 GlobalExceptionHandler 统一转成 HTTP 响应。
 * 好处：业务代码只关心"哪里错了、什么错"，不关心响应格式与状态码。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() { return errorCode; }
}
