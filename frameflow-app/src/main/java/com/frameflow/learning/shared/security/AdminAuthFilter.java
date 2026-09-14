package com.frameflow.learning.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.shared.error.ErrorCode;
import com.frameflow.learning.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 平台级运维接口鉴权：/api/v1/admin/** 需要 X-Admin-Key。
 *
 * ★ 核心：为什么不能用「用户是不是某个团队的 OWNER」来判定？
 * MQ（队列深度、DLQ 重放）是 vhost 内的**全局**基础设施，不属于任何团队；
 * 而 OWNER 是**团队作用域**的角色。用团队角色去授权跨租户操作，
 * 等于任何注册用户（注册即成为自己团队的 OWNER）都能重放全体租户的死信。
 * 这两类权限必须分开：团队内事务走团队角色，平台基础设施走平台密钥。
 *
 * 未配置密钥时一律拒绝（fail-closed），不因"方便本地调试"而放开。
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private final AdminProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuthFilter(AdminProperties props, ObjectMapper objectMapper, Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/v1/admin/");
    }

    /** 恒定时间比较：避免用比较耗时逐字节爆破共享密钥。 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String configured = props.apiKey();
        if (configured == null || configured.isBlank()) {
            deny(response, "平台管理员密钥未配置，运维接口已关闭");
            return;
        }
        String presented = request.getHeader("X-Admin-Key");
        if (!constantTimeEquals(presented, configured)) {
            deny(response, "平台运维接口需要有效的 X-Admin-Key");
            return;
        }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(
                OffsetDateTime.now(clock), ErrorCode.UNAUTHENTICATED.name(), message)));
    }
}
