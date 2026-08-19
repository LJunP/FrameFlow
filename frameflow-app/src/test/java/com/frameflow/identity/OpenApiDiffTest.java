package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import com.frameflow.identity.support.OpenApiNormalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;

/** TEST-FF-M01-001-08 运行时 OpenAPI 与手写权威契约差异校验（规范化后必须一致） */
class OpenApiDiffTest extends IdentityIntegrationTestBase {

    @Test
    void test08_runtimeOpenApiMatchesAuthoritativeContract() throws Exception {
        // 运行时 springdoc 文档（契约不校验 /v3/api-docs 的鉴权）
        ResponseEntity<String> runtime = getJson("/v3/api-docs", new HttpHeaders());
        assertThat(runtime.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 权威 YAML（docs/04-api/openapi/frameflow-v1.yaml，仓库相对路径解析）
        Path contract = findContract();
        assertThat(contract).exists();

        Map<String, Object> runtimeNorm = OpenApiNormalizer.normalize(
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(runtime.getBody(), Map.class));
        Map<String, Object> contractNorm = OpenApiNormalizer.normalize(new Yaml().load(Files.readString(contract)));

        String firstDiff = OpenApiNormalizer.firstDifference(contractNorm, runtimeNorm, "$");
        assertThat(runtimeNorm)
                .as("runtime OpenAPI must match the authoritative handwritten contract"
                        + (firstDiff == null ? "" : "; first diff at " + firstDiff))
                .isEqualTo(contractNorm);
    }

    private Path findContract() {
        Path[] candidates = {
                Path.of("..", "docs", "04-api", "openapi", "frameflow-v1.yaml"),
                Path.of("..", "..", "docs", "04-api", "openapi", "frameflow-v1.yaml"),
                Path.of(System.getProperty("user.dir"), "..", "docs", "04-api", "openapi", "frameflow-v1.yaml"),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Cannot locate authoritative OpenAPI contract; tried " + java.util.Arrays.toString(candidates));
    }
}
