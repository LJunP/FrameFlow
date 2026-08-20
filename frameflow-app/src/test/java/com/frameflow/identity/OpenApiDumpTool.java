package com.frameflow.identity;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** Development helper: fetches runtime springdoc /v3/api-docs for contract curation. */
class OpenApiDumpTool extends IdentityIntegrationTestBase {

    @Test
    void dumpRuntimeOpenApi() throws IOException {
        String target = System.getProperty("frameflow.openapi.dump");
        if (target == null || target.isBlank()) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.exchange(url("/v3/api-docs"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = resp.getBody();
        if (body == null) {
            throw new IllegalStateException("empty /v3/api-docs response: " + resp.getStatusCode());
        }
        Files.writeString(Path.of(target), body);
    }
}
