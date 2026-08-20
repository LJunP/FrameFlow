package com.frameflow.product.infrastructure.persistence;

import com.frameflow.product.domain.Project;
import com.frameflow.product.domain.QualityProfile;
import com.frameflow.product.domain.QualityProfileVersion;
import com.frameflow.product.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepositories {

    private final JdbcTemplate jdbc;

    public ProjectRepositories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Project map(ResultSet rs, int n) throws SQLException {
        Project p = new Project();
        p.setId(rs.getLong("id"));
        p.setTeamId(rs.getLong("team_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        p.setCreatedBy(rs.getLong("created_by"));
        return p;
    }

    public long create(long teamId, long userId, String name, String description) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("projects")
                .usingGeneratedKeyColumns("id")
                .usingColumns("team_id", "name", "description", "status", "created_by");
        Number key = insert.executeAndReturnKey(new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("team_id", teamId).addValue("name", name)
                .addValue("description", description)
                .addValue("status", "ACTIVE")
                .addValue("created_by", userId));
        return key.longValue();
    }

    public Optional<Project> findById(long teamId, long id) {
        List<Project> rows = jdbc.query("SELECT * FROM projects WHERE id = ? AND team_id = ?",
                this::map, id, teamId);
        return rows.stream().findFirst();
    }

    public Project findByIdAnyTeam(long id) {
        List<Project> rows = jdbc.query("SELECT * FROM projects WHERE id = ?", this::map, id);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("project not found"));
    }

    public Long teamOfVersion(long versionId) {
        return jdbc.queryForObject(
                "SELECT p.team_id FROM quality_profile_versions v "
                        + "JOIN quality_profiles p ON p.id = v.profile_id WHERE v.id = ?",
                Long.class, versionId);
    }

    public Project require(long teamId, long id) {
        return findById(teamId, id).orElseThrow(() -> ApiException.notFound("project not found"));
    }

    public List<Project> listByTeam(long teamId) {
        return jdbc.query("SELECT * FROM projects WHERE team_id = ? ORDER BY created_at DESC", this::map, teamId);
    }

    // ---- quality profiles ----
    private QualityProfile mapProfile(ResultSet rs, int n) throws SQLException {
        QualityProfile p = new QualityProfile();
        p.setId(rs.getLong("id"));
        p.setTeamId(rs.getLong("team_id"));
        p.setProjectId(rs.getLong("project_id"));
        p.setName(rs.getString("name"));
        p.setTemplateType(rs.getString("template_type"));
        p.setStatus(rs.getString("status"));
        p.setCreatedBy(rs.getLong("created_by"));
        return p;
    }

    public long createProfile(long teamId, long projectId, long userId, String name, String templateType) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("quality_profiles")
                .usingGeneratedKeyColumns("id")
                .usingColumns("team_id", "project_id", "name", "template_type", "status", "created_by");
        Number key = insert.executeAndReturnKey(new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("team_id", teamId).addValue("project_id", projectId).addValue("name", name)
                .addValue("template_type", templateType).addValue("status", "DRAFT")
                .addValue("created_by", userId));
        return key.longValue();
    }

    public Optional<QualityProfile> findProfile(long teamId, long id) {
        return jdbc.query("SELECT * FROM quality_profiles WHERE id = ? AND team_id = ?", this::mapProfile, id, teamId)
                .stream().findFirst();
    }

    public QualityProfile requireProfile(long teamId, long id) {
        return findProfile(teamId, id).orElseThrow(() -> ApiException.notFound("quality profile not found"));
    }

    public List<QualityProfile> listProfiles(long teamId, long projectId) {
        return jdbc.query("SELECT * FROM quality_profiles WHERE team_id = ? AND project_id = ? ORDER BY created_at",
                this::mapProfile, teamId, projectId);
    }

    // ---- quality profile versions ----
    private QualityProfileVersion mapVersion(ResultSet rs, int n) throws SQLException {
        QualityProfileVersion v = new QualityProfileVersion();
        v.setId(rs.getLong("id"));
        v.setProfileId(rs.getLong("profile_id"));
        v.setVersion(rs.getInt("version_no"));
        v.setStatus(rs.getString("status"));
        v.setPayload(rs.getString("payload"));
        v.setPayloadDigest(rs.getString("payload_digest"));
        v.setCreatedBy(rs.getLong("created_by"));
        return v;
    }

    public Optional<QualityProfileVersion> findVersion(long profileId, int version) {
        return jdbc.query("SELECT * FROM quality_profile_versions WHERE profile_id = ? AND version_no = ?",
                this::mapVersion, profileId, version).stream().findFirst();
    }

    public QualityProfileVersion requireVersion(long profileId, int version) {
        return findVersion(profileId, version).orElseThrow(() -> ApiException.notFound("profile version not found"));
    }

    public int nextVersion(long profileId) {
        Integer maxV = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM quality_profile_versions WHERE profile_id = ?",
                Integer.class, profileId);
        return (maxV == null ? 0 : maxV) + 1;
    }

    public long insertVersion(long profileId, int version, String status, String payload, String digest, long userId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("quality_profile_versions")
                .usingGeneratedKeyColumns("id")
                .usingColumns("profile_id", "version_no", "status", "payload", "payload_digest", "created_by");
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("profile_id", profileId).addValue("version_no", version).addValue("status", status)
                .addValue("payload", Jsonb.pgOrDefault(payload, "{}"))
                .addValue("payload_digest", digest).addValue("created_by", userId);
        Number key = insert.executeAndReturnKey(params);
        return key.longValue();
    }

    public void updateVersionStatus(long id, String status) {
        jdbc.update("UPDATE quality_profile_versions SET status = ?, published_at = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END WHERE id = ?",
                status, status, id);
    }

    public QualityProfileVersion requireVersionById(long versionId) {
        List<QualityProfileVersion> rows = jdbc.query(
                "SELECT * FROM quality_profile_versions WHERE id = ?", this::mapVersion, versionId);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("profile version not found"));
    }

    /** The quality_profiles.id owning the given quality_profile_versions.id. */
    public long profileIdOfVersion(long versionId) {
        Long pid = jdbc.queryForObject(
                "SELECT profile_id FROM quality_profile_versions WHERE id = ?", Long.class, versionId);
        if (pid == null) {
            throw ApiException.notFound("profile version not found");
        }
        return pid;
    }

    /** The version number of the given quality_profile_versions.id. */
    public int versionOf(long versionId) {
        Integer v = jdbc.queryForObject(
                "SELECT version_no FROM quality_profile_versions WHERE id = ?", Integer.class, versionId);
        if (v == null) {
            throw ApiException.notFound("profile version not found");
        }
        return v;
    }

    /** Validate a profile version exists and belongs to a team. */
    public void requireVersionForTeam(long teamId, long versionId) {
        Long cnt = jdbc.queryForObject(
                "SELECT count(*) FROM quality_profile_versions v "
                        + "JOIN quality_profiles p ON p.id = v.profile_id "
                        + "JOIN projects pr ON pr.id = p.project_id "
                        + "WHERE v.id = ? AND pr.team_id = ?",
                Long.class, versionId, teamId);
        if (cnt == null || cnt == 0) {
            throw ApiException.notFound("profile version not found in team");
        }
    }
}