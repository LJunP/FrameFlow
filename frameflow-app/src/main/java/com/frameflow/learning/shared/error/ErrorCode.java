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
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "该邮箱已是团队成员"),
    // 令牌无效/已用/过期统一为同一错误——与登录"用户不存在或密码错误"同理，
    // 不区分细节可避免向试探者泄露邀请状态
    INVITATION_INVALID(HttpStatus.BAD_REQUEST, "邀请链接无效、已被使用或已过期"),
    PASSWORD_RESET_INVALID(HttpStatus.BAD_REQUEST, "重置链接无效、已被使用或已过期"),
    NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "同名资源已存在"),
    // ★ 核心：乐观锁冲突专用错误码——客户端应重新读取数据后重试，
    // 与其它 409（如重名）语义不同，绝不能混用
    VERSION_CONFLICT(HttpStatus.CONFLICT, "数据已被他人修改，请刷新后重试"),
    PROJECT_ARCHIVED(HttpStatus.CONFLICT, "项目已归档，不能修改"),
    INVALID_SPEC(HttpStatus.BAD_REQUEST, "质检标准必须是合法的 JSON 对象"),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "仅接受 video/* 内容类型"),
    PARTS_INVALID(HttpStatus.BAD_REQUEST, "分片列表不合法（须从 1 连续且带 ETag）"),
    UPLOAD_NOT_FOUND(HttpStatus.BAD_REQUEST, "对象尚未上传，无法确认完成"),
    BATCH_CLOSED(HttpStatus.CONFLICT, "批次已关闭，不再接收上传"),
    BATCH_FULL(HttpStatus.CONFLICT, "批次容量已满"),
    INVALID_UPLOAD_STATE(HttpStatus.CONFLICT, "候选当前状态不允许该操作"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "尝试过于频繁，请稍后再试"),
    INVALID_STATE(HttpStatus.CONFLICT, "当前状态不允许该操作"),
    SELECTION_LOCKED(HttpStatus.CONFLICT, "优选集已锁定，内容不可变更"),
    NOT_LOCKED(HttpStatus.CONFLICT, "优选集尚未锁定，不能导出"),
    // ★ 核心：「路径存在但方法不支持」必须独立成码。它之前没有专属 handler，
    // 被下面的 INTERNAL_ERROR 兜底成 500，导致调用方把"接口方法写错了"
    // 误判成"服务挂了"，并污染 5xx 告警——与 NoResourceFoundException 同理。
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "该路径不支持此请求方法"),
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
