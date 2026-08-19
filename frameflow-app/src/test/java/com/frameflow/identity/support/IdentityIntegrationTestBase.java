package com.frameflow.identity.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers PostgreSQL for all M01 integration tests. The container is
 * started once per JVM; every test uses unique emails/resource names so tests stay
 * independent on the shared database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IdentityIntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("frameflow_m01_test")
                .withUsername("frameflow")
                .withPassword("frameflow_test_only")
                .withStartupTimeoutSeconds(180);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        TestJwtKeys.register(registry);
    }

    private static final AtomicLong SEQ = new AtomicLong();

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static String nextEmail() {
        return "user" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected static String nextName() {
        return "team-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    protected Map<String, String> register(String email, String password) {
        return register(email, password, "Test User");
    }

    protected Map<String, String> register(String email, String password, String displayName) {
        Map<String, String> body = Map.of(
                "email", email,
                "password", password,
                "displayName", displayName);
        ResponseEntity<Map> response = rest.postForEntity(url("/api/v1/auth/register"), body, Map.class);
        if (response.getBody() == null) {
            throw new IllegalStateException("register failed: " + response.getStatusCode());
        }
        return response.getBody();
    }

    protected Map<String, String> login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> response = rest.postForEntity(url("/api/v1/auth/login"), body, Map.class);
        if (response.getBody() == null) {
            throw new IllegalStateException("login failed: " + response.getStatusCode());
        }
        return response.getBody();
    }

    protected String accessTokenOf(Map<String, String> tokenPair) {
        return tokenPair.get("accessToken");
    }

    protected String refreshTokenOf(Map<String, String> tokenPair) {
        return tokenPair.get("refreshToken");
    }

    protected HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected ResponseEntity<String> getJson(String path, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> postJson(String path, Object body, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> patchJson(String path, Object body, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> deleteJson(String path, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    protected long idOf(Map<String, ?> map) {
        return ((Number) map.get("id")).longValue();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPED =
            new com.fasterxml.jackson.databind.ObjectMapper();

    protected Map<String, Object> json(String body) {
        try {
            return MAPPED.readValue(body,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse json: " + body, e);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> jsonList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    protected org.springframework.http.HttpHeaders tokenHeaders(String token) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}