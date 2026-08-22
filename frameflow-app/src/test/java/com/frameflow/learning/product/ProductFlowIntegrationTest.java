package com.frameflow.learning.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F2 产品配置集成测试：CRUD、权限矩阵负例、乐观锁、不可变快照、版本化。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- Project CRUD ----------

    @Test
    void project_crud_and_duplicate_name() throws Exception {
        var owner = registerOwner("pj@example.com");

        MvcResult created = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"电商短视频\",\"description\":\"首单 campaign\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lockVersion").value(0))
                .andReturn();
        long projectId = idOf(created);

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("电商短视频"));

        // 同团队重名 → 409
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"电商短视频\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_ALREADY_EXISTS"));
    }

    @Test
    void project_pagination() throws Exception {
        var owner = registerOwner("page@example.com");
        for (int i = 1; i <= 3; i++) {
            createProject(owner, "p-" + i);
        }
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .queryParam("page", "0").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2));
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .queryParam("page", "1").queryParam("size", "2"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void foreign_team_project_is_404() throws Exception {
        var ownerA = registerOwner("ta@example.com");
        var ownerB = registerOwner("tb@example.com");
        long projectOfA = createProject(ownerA, "A-专属");

        mockMvc.perform(get("/api/v1/projects/" + projectOfA).header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void optimistic_lock_rejects_stale_update() throws Exception {
        var owner = registerOwner("lock@example.com");
        long projectId = createProject(owner, "并发测试");

        // 第一次更新成功：lockVersion 0 → 1
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名1\",\"description\":null,\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockVersion").value(1));

        // 用过期版本(0)再更新 → 409 VERSION_CONFLICT（丢失更新被拦截）
        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名2\",\"description\":null,\"lockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 名称没有被第二次写入污染
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.name").value("改名1"));
    }

    // ---------- 权限矩阵 ----------

    @Test
    void role_matrix_write_and_archive() throws Exception {
        var owner = registerOwner("matrix@example.com");
        long teamId = teamIdOf(owner);
        long projectId = createProject(owner, "矩阵测试");

        var operator = seedUser("matrix-op@example.com", teamId, "OPERATOR");
        var reviewer = seedUser("matrix-rv@example.com", teamId, "REVIEWER");
        var viewer = seedUser("matrix-vw@example.com", teamId, "VIEWER");

        // 全员可读
        for (String token : List.of(bearer(operator), bearer(reviewer), bearer(viewer))) {
            mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", token))
                    .andExpect(status().isOk());
        }

        // VIEWER 建项目 → 403
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不许建\"}"))
                .andExpect(status().isForbidden());

        // REVIEWER 发 Brief → 403
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"reviewer 不能发\"}"))
                .andExpect(status().isForbidden());

        // OPERATOR 可以发 Brief
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"operator 的快照\"}"))
                .andExpect(status().isCreated());

        // OPERATOR 归档 → 403（归档仅 OWNER）
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockVersion\":1}"))
                .andExpect(status().isForbidden());

        // OWNER 归档成功
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 归档后发 Brief → 409 PROJECT_ARCHIVED
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"归档后\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
    }

    // ---------- Brief 不可变快照 ----------

    @Test
    void brief_snapshots_are_immutable_and_pointer_moves() throws Exception {
        var owner = registerOwner("brief@example.com");
        long projectId = createProject(owner, "快照测试");

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一版要求\"}"))
                .andExpect(status().isCreated());

        // 没有第二个快照前 current 就是它
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/briefs/current")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("第一版要求"));

        // "修正"= 追加第二版，指针前移
        Thread.sleep(5); // 保证 created_at 可区分
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第二版要求（修正）\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/briefs/current")
                        .header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.content").value("第二版要求（修正）"));

        // 历史快照原样保留（列表两个版本，第一版内容未被"修正"污染）
        MvcResult list = mockMvc.perform(get("/api/v1/projects/" + projectId + "/briefs")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        List<Map<String, Object>> briefs = objectMapper.readValue(
                list.getResponse().getContentAsString(), List.class);
        assertThat(briefs.get(0).get("content")).isEqualTo("第一版要求");
        assertThat(briefs.get(1).get("content")).isEqualTo("第二版要求（修正）");
    }

    // ---------- Quality Profile 版本化 ----------

    @Test
    void profile_create_validate_and_versioning() throws Exception {
        var owner = registerOwner("qp@example.com");

        // 非对象 JSON（数组）→ 400
        mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"坏标准\",\"spec\":\"[]\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPEC"));

        // 非法 JSON 字符串 → 400
        mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"坏标准2\",\"spec\":\"{not-json\"}"))
                .andExpect(status().isBadRequest());

        // 正常创建：v1 随创建发布
        long profileId = createProfile(owner, "电商竖版标准",
                "{\"dimensions\":{\"duration\":{\"min\":8,\"max\":30}},\"weights\":{\"semantic\":0.4}}");

        mockMvc.perform(get("/api/v1/quality-profiles").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestVersion").value(1));

        // 重名 → 409
        mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"电商竖版标准\",\"spec\":\"{}\"}"))
                .andExpect(status().isConflict());

        // 发布 v2（新 spec）
        mockMvc.perform(post("/api/v1/quality-profiles/" + profileId + "/versions")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":\"{\\\"dimensions\\\":{\\\"duration\\\":{\\\"min\\\":10}}}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2));

        // v1 原样可取——发布新版本不改变历史（引用隔离的核心断言）
        mockMvc.perform(get("/api/v1/quality-profiles/" + profileId + "/versions/1")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.spec.dimensions.duration.max").value(30));

        // 不存在的版本 → 404
        mockMvc.perform(get("/api/v1/quality-profiles/" + profileId + "/versions/99")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void version_number_never_reused_even_if_inserted_externally() throws Exception {
        var owner = registerOwner("vr@example.com");
        long profileId = createProfile(owner, "并发版本", "{\"v\":1}");

        // 模拟并发：绕过服务直接占了 v2（服务此时只看到 v1）
        Long ownerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email='vr@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO quality_profile_versions(profile_id, version_no, spec, published_by) "
                        + "VALUES(?, 2, '{\"v\":\"外部占用\"}'::jsonb, ?)", profileId, ownerUserId);

        // 服务端发布：算出 max+1=3，不会覆盖外部 v2，也不复用版本号
        mockMvc.perform(post("/api/v1/quality-profiles/" + profileId + "/versions")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":\"{\\\"v\\\":3}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(3));

        mockMvc.perform(get("/api/v1/quality-profiles/" + profileId + "/versions/2")
                        .header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.spec.v").value("外部占用"));
    }

    @Test
    void foreign_team_profile_is_404() throws Exception {
        var ownerA = registerOwner("qpa@example.com");
        var ownerB = registerOwner("qpb@example.com");
        long profileOfA = createProfile(ownerA, "A的标准", "{\"v\":1}");

        mockMvc.perform(get("/api/v1/quality-profiles/" + profileOfA + "/versions")
                        .header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
    }

    // ---------- 工具 ----------

    private Map<String, Object> registerOwner(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                                + "\"displayName\":\"" + email.split("@")[0] + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    /** 直接在库里种一个指定角色的可登录用户（邀请流程在后续功能）。 */
    private Map<String, Object> seedUser(String email, long teamId, String role) throws Exception {
        jdbcTemplate.update(
                "INSERT INTO users(email, password_hash, display_name) VALUES(?, ?, ?) "
                        + "ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash",
                email, passwordEncoder.encode("Passw0rd!"), email.split("@")[0]);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbcTemplate.update(
                "INSERT INTO team_members(team_id, user_id, role) VALUES(?, ?, ?) ON CONFLICT DO NOTHING",
                teamId, userId, role);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(login.getResponse().getContentAsString(), Map.class);
    }

    private long createProject(Map<String, Object> tokens, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createProfile(Map<String, Object> tokens, String name, String specJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/quality-profiles")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "spec", specJson))))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long teamIdOf(Map<String, Object> tokens) {
        return ((Number) ((Map<String, Object>) tokens.get("team")).get("id")).longValue();
    }

    private String bearer(Map<String, Object> tokens) {
        return "Bearer " + tokens.get("accessToken");
    }
}
