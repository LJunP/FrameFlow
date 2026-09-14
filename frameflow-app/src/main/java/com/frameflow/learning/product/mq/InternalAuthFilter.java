package com.frameflow.learning.product.mq;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.frameflow.learning.shared.error.ErrorCode;
import com.frameflow.learning.shared.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部接口鉴权：/api/v1/internal/** 只放行持有 X-Worker-Key 的服务方。
 *
 * ★ 核心：服务间认证的最小可行方案——共享密钥（从环境注入）。
 * 它挡的是"公网上随便一个请求"，不是高级持续性威胁；生产演进路径是
 * mTLS 或短期签发的服务令牌。为什么不直接 permitAll？因为 api-docs
 * 放行清单是通配的 /internal/**，没有这把钥匙，任何人都能量产假质检结论。
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private final WorkerProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InternalAuthFilter(WorkerProperties props, ObjectMapper objectMapper, Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/internal/");
    }

    /**
     * ★ 核心：密钥比较必须用恒定时间算法。String.equals 在第一个不同字节就返回，
     * 比较耗时与"猜对的前缀长度"相关；攻击者可用统计方法逐字节爆破共享密钥。
     * 单台机器上差异只有微秒级，但这是零成本的加固，没有理由不做。
     * MessageDigest.isEqual 对同长输入走完整个数组才返回。
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader("X-Worker-Key");
        if (!constantTimeEquals(key, props.resultKey())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(
                    OffsetDateTime.now(clock), ErrorCode.UNAUTHENTICATED.name(),
                    "内部接口需要有效的 X-Worker-Key")));
            return;
        }
        chain.doFilter(request, response);
    }
}
