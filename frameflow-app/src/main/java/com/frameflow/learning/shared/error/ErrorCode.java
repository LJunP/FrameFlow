package com.frameflow.learning.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 全项目统一错误码（F1 子集）。
 * 每个错误码绑定一个 HTTP 状态——"状态码语义"在这里集中定义，
 * Controller/Service 只抛 ApiException，不各自拼 HTTP 语义。
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "缺少 Idempotency-Key 请求头"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未认证或令牌无效"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "刷新令牌无效、已吊销或已过期"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行该操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "邮箱已被注册"),
    NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "同名资源已存在"),
    // ★ 核心：乐观锁冲突专用错误码——客户端应重新读取数据后重试，
    // 与其它 409（如重名）语义不同，绝不能混用
    VERSION_CONFLICT(HttpStatus.CONFLICT, "数据已被他人修改，请刷新后重试"),
    PROJECT_ARCHIVED(HttpStatus.CONFLICT, "项目已归档，不能修改"),
    INVALID_SPEC(HttpStatus.BAD_REQUEST, "质检标准必须是合法的 JSON 对象"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public String defaultMessage() { return defaultMessage; }
}
