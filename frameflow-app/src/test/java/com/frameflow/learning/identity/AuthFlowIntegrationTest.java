package com.frameflow.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.frameflow.learning.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F1 认证全链路集成测试（真实 PostgreSQL + 完整 Spring 上下文 + MockMvc）。
 *
 * 覆盖：注册幂等 / 登录防枚举 / JWT 访问控制 / 刷新轮换 / 越权三层
 * （401 未认证、404 防枚举、403 角色不足）。负例（错误场景）和正例
 * 同等重要——越权负例是本项目的硬性测试要求（docs/02 §7）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // ---------- 注册 ----------

    @Test
    void register_without_idempotency_key_is_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a@example.com", "owner-a", "Passw0rd!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void register_returns_tokens_and_replays_same_response_for_same_key() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult first = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("owner@example.com", "owner", "Passw0rd!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("owner@example.com"))
                .andExpect(jsonPath("$.team.role").value("OWNER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        Map<String, Object> firstBody = objectMapper.readValue(
                first.getResponse().getContentAsString(), Map.class);

        // 同 key 重放：返回与首次完全相同的响应（连 accessToken 都一字不差，
        // 因为业务根本没有再次执行），且数据库里只有一个该邮箱的用户
        MvcResult replay = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("owner@example.com", "owner", "Passw0rd!")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.user.email").value("owner@example.com"))
                .andReturn();
        Map<String, Object> replayBody = objectMapper.readValue(
                replay.getResponse().getContentAsString(), Map.class);
        assertThat(replayBody.get("accessToken")).isEqualTo(firstBody.get("accessToken"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'owner@example.com'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void register_duplicate_email_with_new_key_returns_409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "dup", "Passw0rd!")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "dup2", "Passw0rd!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    // ---------- 登录 ----------

    @Test
    void login_wrong_password_and_unknown_email_return_same_401() throws Exception {
        register("login@example.com", "login-user");

        // 密码错误与邮箱不存在必须长得一模一样（防用户枚举，见 AuthService ★ 注释）。
        // 比较 code+message 而不是整个 JSON 串——timestamp 本来就随时间变化，
        // 断言它相等是测试自身的缺陷（本测试第一次写时踩过这个坑）。
        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"WrongPass!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"Whatever1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        var wrongBody = objectMapper.readValue(wrongPassword, Map.class);
        var unknownBody = objectMapper.readValue(unknownEmail, Map.class);
        assertThat(wrongBody.get("code")).isEqualTo(unknownBody.get("code"));
        assertThat(wrongBody.get("message")).isEqualTo(unknownBody.get("message"));
    }

    @Test
    void login_success_returns_tokens() throws Exception {
        register("login-ok@example.com", "login-ok-user");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login-ok@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    // ---------- 访问控制（401 负例） ----------

    @Test
    void me_without_or_with_invalid_token_is_401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void me_with_valid_token_returns_profile() throws Exception {
        var tokens = register("me@example.com", "me-user");

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("me@example.com"))
                .andExpect(jsonPath("$.team.role").value("OWNER"));
    }

    // ---------- 刷新轮换 ----------

    @Test
    void refresh_rotates_tokens_and_old_refresh_is_dead() throws Exception {
        var tokens = register("refresh@example.com", "refresh-user");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.get("refreshToken") + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> newTokens = objectMapper.readValue(
                refreshed.getResponse().getContentAsString(), Map.class);

        // 新令牌对可用
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + newTokens.get("accessToken")))
                .andExpect(status().isOk());

        // 旧 refresh token 必须已死（一次性轮换，防重放共生）
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.get("refreshToken") + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_revokes_all_refresh_tokens() throws Exception {
        var tokens = register("logout@example.com", "logout-user");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(tokens)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.get("refreshToken") + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 越权三层：401 之上还有 404（防枚举）与 403（角色不足） ----------

    @Test
    void foreign_team_returns_404_not_403() throws Exception {
        var tokens = register("enum@example.com", "enum-user");
        Long otherTeamId = jdbcTemplate.queryForObject(
                "INSERT INTO teams(name) VALUES('other-team') RETURNING id", Long.class);

        // 不是我的团队 → 404（哪怕它真实存在）；绝不返回 403 暴露存在性
        mockMvc.perform(get("/api/v1/teams/" + otherTeamId + "/members")
                        .header("Authorization", bearer(tokens)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void non_owner_member_gets_403_on_members_listing() throws Exception {
        var ownerTokens = register("owner2@example.com", "owner2-user");
        Long teamId = teamIdOf(ownerTokens);

        // 种一个真实可登录的 OPERATOR（成员邀请流程 F2 才有，这里用 SQL 种子；
        // 密码哈希用容器里的同一套 BCrypt encoder 生成，保证能走正常登录）
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, display_name) VALUES(?, ?, 'operator') "
                        + "ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
                "op@example.com", passwordEncoder.encode("Passw0rd!"));
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'op@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO team_members(team_id, user_id, role) VALUES(?, ?, 'OPERATOR') "
                        + "ON CONFLICT DO NOTHING", teamId, operatorId);

        MvcResult opLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"op@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> opTokens =
                objectMapper.readValue(opLogin.getResponse().getContentAsString(), Map.class);

        // OPERATOR 是团队成员但非 OWNER → 403（注意：是他自己的团队，
        // 所以不走 404 防枚举分支——404/403 的分界见 AuthService.teamMembers）
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + opTokens.get("accessToken")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 对照组：OWNER 读同一接口正常
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", bearer(ownerTokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    // ---------- 工具方法 ----------

    private Map<String, Object> register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, displayName, "Passw0rd!")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private String body(String email, String displayName, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email, "displayName", displayName, "password", password));
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }

    private Long teamIdOf(Map<String, Object> tokens) {
        Map<String, Object> team = (Map<String, Object>) tokens.get("team");
        return ((Number) team.get("id")).longValue();
    }
}
