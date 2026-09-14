package com.frameflow.learning.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 平台运维接口鉴权的单元测试（不启 Spring 上下文，秒级完成）。
 *
 * 锁死两件事：
 * 1. 未配置密钥时一律拒绝——部署漏配的后果是"运维接口关掉"，而不是"谁都能用"；
 * 2. 只带用户 JWT、不带 X-Admin-Key 一律拒绝（团队角色不等于平台权限）。
 */
class AdminAuthFilterTest {

    private static final String CONFIGURED = "a-very-long-admin-key-for-tests-0001";

    /** 与 Spring 容器里的 ObjectMapper 对齐：ErrorResponse 含 OffsetDateTime，
     *  裸 ObjectMapper 没有 JSR-310 模块，序列化会直接抛异常。 */
    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private static MockHttpServletResponse run(String configuredKey, String presentedHeader)
            throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter(new AdminProperties(configuredKey),
                objectMapper(), Clock.systemUTC());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/mq/stats");
        if (presentedHeader != null) {
            request.addHeader("X-Admin-Key", presentedHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // 被放行时 MockFilterChain 会记录到过的请求；被拒绝时为 null
        assertThat(chain.getRequest() == null).isEqualTo(response.getStatus() == 401);
        return response;
    }

    @Test
    void 未配置密钥时即使带任意请求头也拒绝() throws Exception {
        assertThat(run("", CONFIGURED).getStatus()).isEqualTo(401);
        assertThat(run("", null).getStatus()).isEqualTo(401);
    }

    @Test
    void 缺少或错误的密钥一律拒绝() throws Exception {
        assertThat(run(CONFIGURED, null).getStatus()).isEqualTo(401);
        assertThat(run(CONFIGURED, "wrong-key").getStatus()).isEqualTo(401);
        // 长度不同的错误密钥也要走恒定时间比较，不能因为长度不等就抛异常
        assertThat(run(CONFIGURED, "x").getStatus()).isEqualTo(401);
    }

    @Test
    void 正确密钥放行() throws Exception {
        assertThat(run(CONFIGURED, CONFIGURED).getStatus()).isNotEqualTo(401);
    }

    @Test
    void 非运维路径不拦截() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter(new AdminProperties(""),
                objectMapper(), Clock.systemUTC());
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/batches/10/progress");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // shouldNotFilter 命中：直接放行，不因全局未配置密钥而误伤业务接口
        assertThat(chain.getRequest()).isNotNull();
    }
}
