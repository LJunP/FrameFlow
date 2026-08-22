package com.frameflow.learning.identity.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.identity.repo.IdempotencyMapper;
import com.frameflow.learning.identity.repo.IdempotencyRecordRow;
import com.frameflow.learning.shared.error.ErrorCode;
import com.frameflow.learning.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * T5 幂等中间件：对声明需要幂等的 POST 接口做"同 key 重放返回首次响应"。
 *
 * 为什么做成 Filter 而不是 Controller 里的代码：Filter 包住任意接口，
 * 业务代码零侵入；将来新接口要幂等，只需在 shouldNotFilter 里放行即可。
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    /** 幂等作用域 = 方法 + 路径；不同接口的 key 互不干扰。 */
    private static final String SCOPE = "POST:/api/v1/auth/register";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IdempotencyMapper idempotencyMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyFilter(IdempotencyMapper idempotencyMapper, ObjectMapper objectMapper, Clock clock) {
        this.idempotencyMapper = idempotencyMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只拦截注册接口的 POST；其它请求直通，零开销。
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/register".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            writeError(response, ErrorCode.MISSING_IDEMPOTENCY_KEY);
            return;
        }

        // 首次之后的重放：直接返回数据库里存的"首次响应"，业务代码不再执行。
        //（想象用户双击提交、或网络超时后客户端重试——两次请求只落一次库。）
        IdempotencyRecordRow existing =
                idempotencyMapper.findValid(SCOPE, key, OffsetDateTime.now(clock));
        if (existing != null) {
            response.setStatus(existing.getResponseStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Idempotent-Replay", "true");
            response.getWriter().write(existing.getResponseBody());
            return;
        }

        // 首次请求：用包装器捕获响应体，业务执行完后再入库。
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapped);

        String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
        // ★ 核心：只缓存"确定性的结果"——5xx 意味着业务可能根本没执行成功，
        // 重放一次失败响应会让客户端永远拿不到成功；不缓存它，客户端重试时
        // 会真正再执行一次业务。这是幂等缓存最常见的实现错误点。
        if (wrapped.getStatus() < 500 && !body.isEmpty()) {
            idempotencyMapper.insertIfAbsent(SCOPE, key, wrapped.getStatus(), body,
                    OffsetDateTime.now(clock).plus(Duration.ofHours(24)));
        }
        // 把捕获的内容真正写回客户端（必须调用，否则响应体是空的！）
        wrapped.copyBodyToResponse();
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse(OffsetDateTime.now(clock), code.name(), code.defaultMessage())));
    }
}
