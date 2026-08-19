package com.frameflow.identity.security;

import com.frameflow.identity.error.ErrorCodes;
import com.frameflow.identity.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Authenticated but not allowed -> 403 with unified error body. */
public final class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.write(response, HttpStatus.FORBIDDEN,
                new ErrorResponse(ErrorCodes.FORBIDDEN, "已认证但权限不足", RequestIdFilter.requestId(request), null));
    }
}
