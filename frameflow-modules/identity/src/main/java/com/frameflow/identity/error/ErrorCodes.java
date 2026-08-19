package com.frameflow.identity.error;

/** Stable error codes registered in docs/04-api/error-code-catalog.md. */
public final class ErrorCodes {
    private ErrorCodes() {}

    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String IDEMPOTENCY_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    public static final String USER_EMAIL_CONFLICT = "USER_EMAIL_CONFLICT";
    public static final String TEAM_MEMBER_ALREADY_EXISTS = "TEAM_MEMBER_ALREADY_EXISTS";
    public static final String TEAM_LAST_OWNER_CONFLICT = "TEAM_LAST_OWNER_CONFLICT";
    public static final String TEAM_SELF_REMOVAL_FORBIDDEN = "TEAM_SELF_REMOVAL_FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
