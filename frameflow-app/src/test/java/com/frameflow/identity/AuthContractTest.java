package com.frameflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.frameflow.identity.support.IdentityIntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** TEST-FF-M01-001-01 注册登录契约测试 */
class AuthContractTest extends IdentityIntegrationTestBase {

    @Test
    void test01_registerLoginContract() {
        String email = nextEmail();
        String password = "passw0rd!";

        // 注册 201 + User 结构（无密码泄露）
        ResponseEntity<Map> reg = rest.postForEntity(url("/api/v1/auth/register"),
                Map.of("email", email, "password", password, "displayName", "Alice"), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        Map user = reg.getBody();
        assertThat(user).containsKeys("id", "email", "displayName", "status");
        assertThat(user.get("email")).isEqualTo(email);
        assertThat(user.get("displayName")).isEqualTo("Alice");
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user).doesNotContainKey("password");
        assertThat(user.toString()).doesNotContain(password);

        // 重复注册 409 USER_EMAIL_CONFLICT
        ResponseEntity<Map> dup = rest.postForEntity(url("/api/v1/auth/register"),
                Map.of("email", email, "password", password, "displayName", "Alice2"), Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("code")).isEqualTo("USER_EMAIL_CONFLICT");
        assertThat(dup.getBody().get("requestId"))
                .isEqualTo(dup.getHeaders().getFirst("X-Request-Id"));

        // 登录 200 TokenPair
        Map pair = login(email, password);
        assertThat(pair).containsKeys("accessToken", "refreshToken", "expiresIn", "tokenType");
        assertThat(pair.get("tokenType")).isEqualTo("Bearer");
        assertThat(((Number) pair.get("expiresIn")).longValue()).isEqualTo(900);
        assertThat(((String) pair.get("refreshToken")).length()).isEqualTo(43);

        // /me 200
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenOf(pair));
        ResponseEntity<Map> me = rest.exchange(url("/api/v1/auth/me"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);

        // 错误密码 401 AUTH_REQUIRED，错误 body 的 requestId 与 header 一致
        ResponseEntity<Map> bad = rest.postForEntity(url("/api/v1/auth/login"),
                Map.of("email", email, "password", "wrong-password"), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bad.getBody().get("code")).isEqualTo("AUTH_REQUIRED");
        assertThat(bad.getBody().get("requestId")).isEqualTo(bad.getHeaders().getFirst("X-Request-Id"));

        // 邮箱大小写与空格归一化：大写邮箱注册后可用小写登录
        String upper = "  " + email.toUpperCase();
        ResponseEntity<Map> loginUpper = rest.postForEntity(url("/api/v1/auth/login"),
                Map.of("email", upper, "password", password), Map.class);
        assertThat(loginUpper.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
