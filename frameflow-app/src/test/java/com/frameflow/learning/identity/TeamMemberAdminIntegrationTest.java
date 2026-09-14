package com.frameflow.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 团队成员管理（角色变更 / 移除）集成测试。
 *
 * 覆盖权限矩阵全格：正例（OWNER 变更/移除成功）+ 全部负例——
 * 401 未认证、404 防枚举（外部团队/非成员目标）、403 角色不足与两条
 * "不可动"规则（自己、OWNER）、400 非法角色。负例与正例同等重要，
 * 见 docs/02 §7 的硬性越权测试要求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamMemberAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // ---------- 角色变更 ----------

    @Test
    void owner_can_change_member_role_and_list_reflects_it() throws Exception {
        var owner = register("role-owner@example.com", "role-owner");
        long teamId = teamIdOf(owner);
        long operatorId = seedMember(teamId, "role-op@example.com", "OPERATOR");

        mockMvc.perform(put(membersUrl(teamId) + "/" + operatorId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(operatorId))
                .andExpect(jsonPath("$.role").value("REVIEWER"));

        // 落库核验 + 列表接口同步可见（不只看响应体）
        String stored = jdbcTemplate.queryForObject(
                "SELECT role FROM team_members WHERE team_id = ? AND user_id = ?",
                String.class, teamId, operatorId);
        assertThat(stored).isEqualTo("REVIEWER");

        mockMvc.perform(get(membersUrl(teamId)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == " + operatorId + ")].role").value("REVIEWER"));
    }

    @Test
    void owner_role_and_unknown_role_are_rejected_with_400() throws Exception {
        var owner = register("role-owner2@example.com", "role-owner2");
        long teamId = teamIdOf(owner);
        long operatorId = seedMember(teamId, "role-op2@example.com", "OPERATOR");

        // 提权为 OWNER 被 DTO @Pattern 拦截（本接口不产生 OWNER，见该 DTO ★ 注释）
        mockMvc.perform(put(membersUrl(teamId) + "/" + operatorId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put(membersUrl(teamId) + "/" + operatorId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 非法请求不得有任何副作用
        String stored = jdbcTemplate.queryForObject(
                "SELECT role FROM team_members WHERE team_id = ? AND user_id = ?",
                String.class, teamId, operatorId);
        assertThat(stored).isEqualTo("OPERATOR");
    }

    @Test
    void owner_cannot_change_own_role() throws Exception {
        var owner = register("self-role@example.com", "self-role");
        long teamId = teamIdOf(owner);
        long ownerId = userIdOf(owner);

        mockMvc.perform(put(membersUrl(teamId) + "/" + ownerId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void owner_cannot_change_another_owners_role() throws Exception {
        var owner = register("multi-owner-a@example.com", "multi-owner-a");
        long teamId = teamIdOf(owner);
        // 种子第二个 OWNER（注册流程每个团队只有一个 OWNER，这里用 SQL 构造
        // 防守场景：即使数据里出现多个 OWNER，接口也必须拒绝变更其角色）
        long secondOwnerId = seedMember(teamId, "multi-owner-b@example.com", "OWNER");

        mockMvc.perform(put(membersUrl(teamId) + "/" + secondOwnerId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- 移除成员 ----------

    @Test
    void owner_can_remove_member_and_access_is_cut_immediately() throws Exception {
        var owner = register("rm-owner@example.com", "rm-owner");
        long teamId = teamIdOf(owner);
        long operatorId = seedMember(teamId, "rm-op@example.com", "OPERATOR");
        var opTokens = login("rm-op@example.com");

        mockMvc.perform(delete(membersUrl(teamId) + "/" + operatorId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        // 只断成员关系、不删 users 账号（账号是全局身份，见 AuthService.removeMember）
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = ?",
                Integer.class, teamId, operatorId);
        assertThat(remaining).isZero();
        Integer account = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE id = ?", Integer.class, operatorId);
        assertThat(account).isEqualTo(1);

        // 访问立即被切断：被移除者的旧 access token 仍在有效期内，
        // 但成员校验 404——鉴权以数据库成员关系为准，不认 JWT 里的旧角色快照
        mockMvc.perform(get(membersUrl(teamId)).header("Authorization", bearer(opTokens)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void owner_cannot_remove_self_or_another_owner() throws Exception {
        var owner = register("rm-owner2@example.com", "rm-owner2");
        long teamId = teamIdOf(owner);
        long secondOwnerId = seedMember(teamId, "rm-owner3@example.com", "OWNER");

        mockMvc.perform(delete(membersUrl(teamId) + "/" + userIdOf(owner))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete(membersUrl(teamId) + "/" + secondOwnerId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- 越权负例：403（在团队非 OWNER）与 404（防枚举） ----------

    @Test
    void non_owner_member_gets_403_on_role_change_and_remove() throws Exception {
        var owner = register("forbid-owner@example.com", "forbid-owner");
        long teamId = teamIdOf(owner);
        long operatorId = seedMember(teamId, "forbid-op@example.com", "OPERATOR");
        long viewerId = seedMember(teamId, "forbid-view@example.com", "VIEWER");
        var opTokens = login("forbid-op@example.com");

        mockMvc.perform(put(membersUrl(teamId) + "/" + viewerId + "/role")
                        .header("Authorization", bearer(opTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete(membersUrl(teamId) + "/" + viewerId)
                        .header("Authorization", bearer(opTokens)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 对照组：OWNER 对同一目标操作成功
        mockMvc.perform(delete(membersUrl(teamId) + "/" + viewerId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void foreign_team_and_non_member_target_return_404() throws Exception {
        var owner = register("enum-owner@example.com", "enum-owner");
        long teamId = teamIdOf(owner);
        var outsider = register("enum-outsider@example.com", "enum-outsider");
        long outsiderTeamId = teamIdOf(outsider);
        long outsiderId = userIdOf(outsider);

        // 外部团队（存在但不是我的）→ 404，绝不返回 403 暴露存在性
        mockMvc.perform(put(membersUrl(outsiderTeamId) + "/" + outsiderId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(membersUrl(outsiderTeamId) + "/" + outsiderId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());

        // 自己团队里不存在的目标成员 → 404（不泄露成员关系存在性）
        mockMvc.perform(put(membersUrl(teamId) + "/" + outsiderId + "/role")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(membersUrl(teamId) + "/" + outsiderId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_requests_are_401() throws Exception {
        mockMvc.perform(put("/api/v1/teams/1/members/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/v1/teams/1/members/2"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 工具方法 ----------

    /** 种一个真实可登录的成员（邀请全流程见 TeamInvitationIntegrationTest；此处走 SQL 以便覆盖角色矩阵）。 */
    private long seedMember(long teamId, String email, String role) {
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, display_name) VALUES(?, ?, ?) "
                        + "ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
                email, passwordEncoder.encode("Passw0rd!"), email);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbcTemplate.update(
                "INSERT INTO team_members(team_id, user_id, role) VALUES(?, ?, ?) "
                        + "ON CONFLICT DO NOTHING", teamId, userId, role);
        return userId;
    }

    private Map<String, Object> register(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "displayName", displayName, "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private Map<String, Object> login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private String membersUrl(long teamId) {
        return "/api/v1/teams/" + teamId + "/members";
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }

    private long teamIdOf(Map<String, Object> tokens) {
        Map<String, Object> team = (Map<String, Object>) tokens.get("team");
        return ((Number) team.get("id")).longValue();
    }

    private long userIdOf(Map<String, Object> tokens) {
        Map<String, Object> user = (Map<String, Object>) tokens.get("user");
        return ((Number) user.get("id")).longValue();
    }
}
