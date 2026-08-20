package com.frameflow.identity.security;

import com.frameflow.identity.error.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the Bearer access token with the strict RS256 decoder
 * (alg/kid/iss/aud/exp all enforced by JwtKeyConfig). Failed/expired tokens are
 * answered here with 401 so the response body carries the precise error code.
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;

    public JwtAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        String token = authorization.substring(7).trim();
        try {
            Jwt jwt = decoder.decode(token);
            long userId = Long.parseLong(jwt.getSubject());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new com.frameflow.identity.api.IdentityPrincipal(userId), token,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            boolean expired = e.getMessage() != null
                    && e.getMessage().toLowerCase(Locale.ROOT).contains("expired");
            respond(response, request, expired ? ErrorCodes.TOKEN_EXPIRED : ErrorCodes.AUTH_REQUIRED,
                    expired ? "Access Token 已过期" : "未认证或凭据无效");
        }
    }

    private void respond(HttpServletResponse response, HttpServletRequest request, String code, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"requestId\":\"" + RequestIdFilter.requestId(request) + "\"}");
    }
}