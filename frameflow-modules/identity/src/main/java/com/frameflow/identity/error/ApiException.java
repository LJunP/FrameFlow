package com.frameflow.identity.error;

import org.springframework.http.HttpStatus;

/** Business/API exception mapped to a stable error code and HTTP status. */
public class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    public ApiException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.AUTH_REQUIRED, message);
    }

    public static ApiException tokenExpired(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.TOKEN_EXPIRED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
