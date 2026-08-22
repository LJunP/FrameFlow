package com.frameflow.learning.shared.error;

import java.time.OffsetDateTime;

/**
 * 统一错误响应体：所有错误都是 {timestamp, code, message} 一个形状。
 * 前端只需要一套解析逻辑；测试也只断言一个形状。
 */
public record ErrorResponse(OffsetDateTime timestamp, String code, String message) {
}
