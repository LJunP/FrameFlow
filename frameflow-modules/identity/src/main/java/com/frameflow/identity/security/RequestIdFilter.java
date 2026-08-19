package com.frameflow.identity.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Generates a fresh X-Request-Id per HTTP attempt (never replayed from a prior
 * attempt) and exposes it on the request so every component — including security
 * entry points and error handlers — can embed the identical value.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements Filter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTR = "frameflow.requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestId = UUID.randomUUID().toString();
        httpRequest.setAttribute(ATTR, requestId);
        httpResponse.setHeader(HEADER, requestId);
        chain.doFilter(request, response);
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR);
        return value == null ? "" : value.toString();
    }
}
