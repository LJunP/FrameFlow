package com.frameflow.learning.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个 HTTP 请求建立可回传、可检索、不会注入日志控制字符的 request_id。 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        // ★ 核心：只把经过白名单的 ID 放进 MDC。若原样信任 Header，攻击者可用
        // 换行伪造 JSON 日志；若 finally 不 clear，线程池复用会把 A 请求 ID 串到 B。
        try (MDC.MDCCloseable ignoredRequestId = MDC.putCloseable("request_id", requestId);
             MDC.MDCCloseable ignoredMethod = MDC.putCloseable("http_method", request.getMethod())) {
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        }
    }
}
