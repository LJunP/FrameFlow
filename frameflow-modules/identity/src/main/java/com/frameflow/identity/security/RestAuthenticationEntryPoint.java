package com.frameflow.identity.security;

import com.frameflow.identity.error.ErrorCodes;
import com.frameflow.identity.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Unauthenticated/insufficient authentication -> 401 with unified error body. */
public final class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED,
                new ErrorResponse(ErrorCodes.AUTH_REQUIRED, "未认证或凭据无效", RequestIdFilter.requestId(request), null));
    }

    static void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"" + body.code() + "\",\"message\":\"" + escape(body.message())
                + "\",\"requestId\":\"" + body.requestId() + "\"}");
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
