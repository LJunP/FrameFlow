package com.frameflow.learning.observability;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preserves_safe_request_id_inside_chain_and_clears_mdc_afterward() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader(CorrelationIdFilter.HEADER, "pilot-request-0001");
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();

        filter.doFilter(request, response, (req, resp) -> seen.set(MDC.get("request_id")));

        assertThat(seen.get()).isEqualTo("pilot-request-0001");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("pilot-request-0001");
        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("http_method")).isNull();
    }

    @Test
    void replaces_control_character_injection_with_generated_uuid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader(CorrelationIdFilter.HEADER, "bad\nforged-log");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
