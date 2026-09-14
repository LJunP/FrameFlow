package com.frameflow.learning.shared.error;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → 统一错误响应。Controller 里从此不写 try/catch 拼错误格式。
 *
 * 【F1 阅读顺序】ErrorCode → ApiException → 本类 → SecurityConfig 里的
 * 401/403 handler（安全过滤器抛的错误进不了这里，必须单独处理，见其注释）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ★ 核心：兜底 handler 必须把堆栈记进日志——对外隐藏细节（安全）与
    // 对内留下线索（排障）是两回事。没有这行日志，500 就是黑盒。
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** 业务异常：按错误码绑定的 HTTP 状态返回。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.errorCode(), ex.getMessage());
    }

    /** @Valid 校验失败：聚合成一条 400，message 里带字段明细。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder("参数校验失败: ");
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> sb.append(fe.getField()).append(" ").append(fe.getDefaultMessage()).append("; "));
        return build(ErrorCode.VALIDATION_ERROR, sb.toString());
    }

    /**
     * ★ 核心：不存在的路径/静态资源必须回 404，不能落到兜底变成 500。
     * Spring 6 对未匹配的请求抛 NoResourceFoundException（既不是业务异常
     * 也不是服务故障）；不单独处理就会被下面的 Exception 兜底吞成 500，
     * 让调用方把"路径写错了"误判成"服务挂了"，也会污染 5xx 告警。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    /**
     * 路径存在、方法不支持（如 GET /api/v1/batches 只有 POST 实现）。
     * 与上面的 NoResourceFoundException 同一类问题：都是"调用方用错了"，
     * 不是服务故障。不接住就会落进下面的 Exception 兜底变成 500，
     * 把"方法写错"伪装成"服务挂了"，既误导排查也污染 5xx 告警。
     */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            org.springframework.web.servlet.NoHandlerFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    // ★ 核心：兜底异常绝不把内部细节（堆栈/SQL/类名）回给客户端——
    // 泄露内部结构是信息收集型攻击的第一步。对外统一"服务内部错误"，
    // 详细堆栈进日志（将来接结构化日志时在 service 层记录）。
    // 改坏后果：直接返回 ex.getMessage() 可能把 SQL、文件路径暴露给攻击者。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        log.error("未处理异常，返回 500", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.status())
                .body(new ErrorResponse(OffsetDateTime.now(clock), code.name(), message));
    }
}
