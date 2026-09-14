package com.frameflow.learning.identity.security;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.shared.error.ErrorCode;
import com.frameflow.learning.shared.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置：无状态 JWT + RBAC。
 *
 * 【F1 阅读顺序】RsaKeyProvider → TokenService（签发方）→ 本类（校验方）
 * → AuthController/AuthService（使用者）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityConfig(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RsaKeyProvider keys) throws Exception {
        http
                // ★ 核心：API 服务三件套——关 CSRF、关 session、无状态。
                // CSRF 防的是"浏览器自动带 Cookie"的跨站攻击；我们用 Header 传
                // Bearer token，浏览器不会自动携带，CSRF 无从发生，开着只会
                // 挡掉所有非 GET 请求。session 关闭是因为认证信息每次请求
                // 都在 JWT 里，服务器不需要记住任何人（水平扩展的前提）。
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/ping").permitAll()
                        // F10：Prometheus 与容器探针在隔离网络内无用户 JWT。
                        // 只放行 GET 的 health/prometheus 精确路径；Nginx 不反代
                        // Actuator，其他管理端点仍保持未暴露 + 默认拒绝。
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**",
                                "/actuator/prometheus").permitAll()
                        // 内部接口不走用户 JWT，由 InternalAuthFilter 的
                        // X-Worker-Key 把关（双层：Security 放行 + Filter 验钥）
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        // OpenAPI 文档端点：契约一致性测试需要匿名访问。
                        // 生产环境应在网关层按环境开关（dev 开、prod 关），
                        // F9 部署时处理，此处先放行供测试与本地联调。
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // ★ 核心：放行清单用"方法 + 精确路径"而不是 /auth/** 通配——
                        // auth 下未来新增接口（如改密码）默认就落在"需要认证"侧，
                        // 这是"默认拒绝、显式放行"的安全原则：新东西不声明就是关着的。
                        // 注意必须用 HttpMethod.POST 枚举：写成字符串 "POST" 会被
                        // 当成路径模式参与匹配（等于多放行了一条 POST 规则）。
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register",
                                "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/verify-email/confirm").permitAll()
                        // 接受邀请：受邀人没有会话可带，唯一凭证是令牌本身（见
                        // InvitationController 注释）。仅放行这一个精确路径。
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/accept").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // ★ 核心：无效/过期 Bearer token 的 401 由资源服务器的
                        // BearerTokenAuthenticationFilter 直接产生，不走上面
                        // exceptionHandling 的 entryPoint——若不在这里接上统一
                        // JSON 出口，客户端会收到一个空体的 401，错误形状不统一。
                        .authenticationEntryPoint((req, resp, ex) ->
                                writeError(resp, ErrorCode.UNAUTHENTICATED)))
                // ★ 核心：401/403 必须在这里自定义——安全过滤器链的拒绝发生在
                // 进入 Controller 之前，@RestControllerAdvice 根本接不到，
                // 默认行为是返回 Spring 的 HTML/空体错误页。这里统一写成
                // 和 GlobalExceptionHandler 相同的 JSON 形状，前端无感知差异。
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, ex) ->
                                writeError(resp, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, resp, ex) ->
                                writeError(resp, ErrorCode.FORBIDDEN)));
        return http.build();
    }

    /**
     * 验签器：用 RsaKeyProvider 的同一把公钥校验签名与有效期。
     * withPublicKey 是"单一静态公钥"场景的标准构造（JWKS 端点轮换密钥的
     * withJwkSource 适用于多服务方/动态密钥，对本项目是过度设计）。
     * 过期（exp）校验由 decoder 自动完成，无需手写时间比较。
     */
    @Bean
    public JwtDecoder jwtDecoder(RsaKeyProvider keys) {
        try {
            return NimbusJwtDecoder.withPublicKey(keys.rsaKey().toRSAPublicKey()).build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("无法构造 JWT 验签器", e);
        }
    }

    /** 把 JWT 里的 role claim 翻译成 Spring Security 的 ROLE_ 权限。 */
    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null) {
                return List.of();
            }
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return authorities;
        });
        return converter;
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws java.io.IOException {
        response.setStatus(code.status().value());
        // 先设 UTF-8 再取 Writer：中文消息否则会按默认 ISO-8859-1 写出变成乱码
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse(OffsetDateTime.now(clock), code.name(), code.defaultMessage())));
    }
}
