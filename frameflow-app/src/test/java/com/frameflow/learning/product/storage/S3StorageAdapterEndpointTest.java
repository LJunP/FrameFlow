package com.frameflow.learning.product.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class S3StorageAdapterEndpointTest {

    private HttpServer internalS3;

    @AfterEach
    void stopServer() {
        if (internalS3 != null) {
            internalS3.stop(0);
        }
    }

    @Test
    void presigned_url_uses_browser_public_endpoint_while_bucket_check_uses_internal_endpoint()
            throws IOException {
        internalS3 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        internalS3.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        internalS3.start();

        String internalEndpoint = "http://127.0.0.1:" + internalS3.getAddress().getPort();
        StorageProperties props = properties(internalEndpoint, "https://media.example.test");

        String url = new S3StorageAdapter(props)
                .presignPut("batches/1/candidates/demo.mp4", Duration.ofMinutes(5));

        URI signed = URI.create(url);
        assertThat(signed.getScheme()).isEqualTo("https");
        assertThat(signed.getHost()).isEqualTo("media.example.test");
        assertThat(signed.getPath()).isEqualTo("/frameflow-test/batches/1/candidates/demo.mp4");
        assertThat(signed.getQuery()).contains("X-Amz-Signature=");
    }

    @Test
    void invalid_public_endpoint_is_rejected_before_any_network_call() {
        assertThatThrownBy(() -> new S3StorageAdapter(properties(
                "http://127.0.0.1:9000", "https://user:pass@media.example.test/path?token=x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public-endpoint");
    }

    private StorageProperties properties(String internalEndpoint, String publicEndpoint) {
        return new StorageProperties(
                internalEndpoint,
                publicEndpoint,
                "us-east-1",
                "frameflow",
                "frameflow_local_only",
                "frameflow-test",
                Duration.ofMinutes(30),
                DataSize.ofMegabytes(32),
                DataSize.ofMegabytes(8),
                Duration.ofHours(1));
    }
}
