package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.support.OpenApiNormalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Proves the contract projection fails closed for the fields it is meant to guard. */
class OpenApiNormalizerSensitivityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void responseHeadersSecurityMediaTypesAndParameterConstraintsAffectTheDiff() {
        Map<String, Object> authority = new Yaml().load(BASE);
        Map<String, Object> expected = OpenApiNormalizer.normalize(authority);

        Map<String, Object> missingHeader = copy(authority);
        response(missingHeader).remove("headers");
        assertDifferent(expected, missingHeader, ".headers");

        Map<String, Object> publicOperation = copy(authority);
        operation(publicOperation).put("security", List.of());
        assertDifferent(expected, publicOperation, ".security");

        Map<String, Object> wrongMediaType = copy(authority);
        Map<String, Object> content = map(response(wrongMediaType).get("content"));
        content.put("application/problem+json", content.remove("application/json"));
        assertDifferent(expected, wrongMediaType, ".content");

        Map<String, Object> weakerParameter = copy(authority);
        List<Object> parameters = (List<Object>) operation(weakerParameter).get("parameters");
        map(map(parameters.get(0)).get("schema")).put("maxLength", 256);
        assertDifferent(expected, weakerParameter, ".parameters");
    }

    private static void assertDifferent(Map<String, Object> expected, Map<String, Object> mutant,
                                        String expectedPathFragment) {
        String difference = OpenApiNormalizer.firstDifference(
                expected, OpenApiNormalizer.normalize(mutant), "$");
        assertThat(difference).isNotNull().contains(expectedPathFragment);
    }

    private static Map<String, Object> operation(Map<String, Object> doc) {
        return map(map(map(doc.get("paths")).get("/widgets")).get("post"));
    }

    private static Map<String, Object> response(Map<String, Object> doc) {
        return map(map(operation(doc).get("responses")).get("200"));
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return MAPPER.convertValue(source, new TypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static final String BASE = """
            openapi: 3.0.3
            security:
              - bearerAuth: []
            paths:
              /widgets:
                post:
                  operationId: createWidget
                  parameters:
                    - name: Idempotency-Key
                      in: header
                      required: true
                      schema:
                        type: string
                        format: uuid
                        maxLength: 128
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Input'
                  responses:
                    '200':
                      headers:
                        X-Request-Id:
                          $ref: '#/components/headers/RequestId'
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Output'
            components:
              securitySchemes:
                bearerAuth:
                  type: http
                  scheme: bearer
                  bearerFormat: JWT
              headers:
                RequestId:
                  required: true
                  schema:
                    type: string
                    format: uuid
              schemas:
                Input:
                  type: object
                  required: [name]
                  properties:
                    name:
                      type: string
                      minLength: 1
                      maxLength: 100
                Output:
                  type: object
                  required: [id]
                  properties:
                    id:
                      type: integer
                      format: int64
            """;
}
