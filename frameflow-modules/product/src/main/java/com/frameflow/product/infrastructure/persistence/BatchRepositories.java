package com.frameflow.product.infrastructure.persistence;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.BatchCandidate.Batch;
import com.frameflow.product.domain.BatchCandidate.Candidate;
import com.frameflow.product.domain.BatchCandidate.CandidateVersion;
import com.frameflow.product.domain.BatchCandidate.UploadSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class BatchRepositories {

    private final JdbcTemplate jdbc;

    public BatchRepositories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Batch mapBatch(ResultSet rs, int n) throws SQLException {
        Batch b = new Batch();
        b.setId(rs.getLong("id"));
        b.setTeamId(rs.getLong("team_id"));
        b.setProjectId(rs.getLong("project_id"));
        b.setName(rs.getString("name"));
        b.setStatus(rs.getString("status"));
        b.setPromptText(rs.getString("prompt_text"));
        Object pv = rs.getObject("profile_version_id");
        b.setProfileVersionId(pv == null ? null : rs.getLong("profile_version_id"));
        Object bi = rs.getObject("brief_id");
        b.setBriefId(bi == null ? null : rs.getLong("brief_id"));
        Object cl = rs.getObject("capacity_limit");
        b.setCapacityLimit(cl == null ? 300 : rs.getInt("capacity_limit"));
        Object fc = rs.getObject("failure_count");
        b.setFailureCount(fc == null ? 0 : rs.getInt("failure_count"));
        b.setCreatedBy(rs.getLong("created_by"));
        return b;
    }

    public long create(long teamId, long projectId, long userId, String name, String prompt,
                       Long profileVersionId, Long briefId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("batches")
                .usingGeneratedKeyColumns("id")
                .usingColumns("team_id", "project_id", "name", "status", "prompt_text",
                        "profile_version_id", "brief_id", "created_by");
        Number key = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("team_id", teamId).addValue("project_id", projectId)
                .addValue("name", name).addValue("status", "DRAFT")
                .addValue("prompt_text", prompt)
                .addValue("profile_version_id", profileVersionId)
                .addValue("brief_id", briefId)
                .addValue("created_by", userId));
        return key.longValue();
    }

    public Optional<Batch> findById(long teamId, long id) {
        return jdbc.query("SELECT * FROM batches WHERE id = ? AND team_id = ?", this::mapBatch, id, teamId)
                .stream().findFirst();
    }

    public Batch findByIdAnyTeam(long id) {
        return jdbc.query("SELECT * FROM batches WHERE id = ?", this::mapBatch, id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("batch not found"));
    }

    public Batch require(long teamId, long id) {
        return findById(teamId, id).orElseThrow(() -> ApiException.notFound("batch not found"));
    }

    public List<Batch> listByProject(long teamId, long projectId) {
        return jdbc.query("SELECT * FROM batches WHERE team_id = ? AND project_id = ? ORDER BY created_at DESC",
                this::mapBatch, teamId, projectId);
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE batches SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public void updateStatusAndTimestamps(long id, String status, boolean started, boolean completed) {
        if (started) {
            jdbc.update("UPDATE batches SET status = ?, started_at = COALESCE(started_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        } else if (completed) {
            jdbc.update("UPDATE batches SET status = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        } else {
            jdbc.update("UPDATE batches SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        }
    }

    private Candidate mapCandidate(ResultSet rs, int n) throws SQLException {
        Candidate c = new Candidate();
        c.setId(rs.getLong("id"));
        c.setTeamId(rs.getLong("team_id"));
        c.setBatchId(rs.getLong("batch_id"));
        Object p = rs.getObject("parent_candidate_id");
        c.setParentCandidateId(p == null ? null : rs.getLong("parent_candidate_id"));
        c.setCandidateKey(rs.getString("candidate_key"));
        c.setStatus(rs.getString("status"));
        c.setMediaType(rs.getString("media_type"));
        c.setSizeBytes(rs.getLong("size_bytes"));
        c.setCreatedBy(rs.getLong("created_by"));
        return c;
    }

    public long createCandidate(long teamId, long batchId, long userId, String key, String mediaType) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("candidates")
                .usingGeneratedKeyColumns("id")
                .usingColumns("team_id", "batch_id", "candidate_key", "status", "media_type", "created_by");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("team_id", teamId).addValue("batch_id", batchId)
                .addValue("candidate_key", key).addValue("status", "UPLOADING")
                .addValue("media_type", mediaType).addValue("created_by", userId));
        return k.longValue();
    }

    public Optional<Candidate> findCandidate(long teamId, long id) {
        return jdbc.query("SELECT * FROM candidates WHERE id = ? AND team_id = ?", this::mapCandidate, id, teamId)
                .stream().findFirst();
    }

    public Candidate findCandidateAnyTeam(long id) {
        return jdbc.query("SELECT * FROM candidates WHERE id = ?", this::mapCandidate, id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("candidate not found"));
    }

    public Candidate requireCandidate(long teamId, long id) {
        return findCandidate(teamId, id).orElseThrow(() -> ApiException.notFound("candidate not found"));
    }

    public List<Candidate> listCandidates(long teamId, long batchId) {
        return jdbc.query("SELECT * FROM candidates WHERE team_id = ? AND batch_id = ? ORDER BY id",
                this::mapCandidate, teamId, batchId);
    }

    public long countCandidates(long batchId) {
        Long c = jdbc.queryForObject("SELECT count(*) FROM candidates WHERE batch_id = ?", Long.class, batchId);
        return c == null ? 0 : c;
    }

    public void updateCandidateStatus(long id, String status) {
        jdbc.update("UPDATE candidates SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public void updateCandidateSize(long id, long size) {
        jdbc.update("UPDATE candidates SET size_bytes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", size, id);
    }

    public long nextCandidateVersion(long candidateId) {
        Integer maxV = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM candidate_versions WHERE candidate_id = ?",
                Integer.class, candidateId);
        return (maxV == null ? 0 : maxV) + 1;
    }

    public long insertCandidateVersion(long candidateId, int version, String objectRef, String digest,
                                       long size, String mediaType) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("candidate_versions")
                .usingGeneratedKeyColumns("id")
                .usingColumns("candidate_id", "version_no", "object_ref", "content_digest", "size_bytes", "media_type");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("candidate_id", candidateId).addValue("version_no", version)
                .addValue("object_ref", objectRef).addValue("content_digest", digest)
                .addValue("size_bytes", size).addValue("media_type", mediaType));
        return k.longValue();
    }

    public CandidateVersion requireVersion(long candidateId, int version) {
        List<CandidateVersion> rows = jdbc.query(
                "SELECT * FROM candidate_versions WHERE candidate_id = ? AND version_no = ?",
                (rs, n) -> {
                    CandidateVersion v = new CandidateVersion();
                    v.setId(rs.getLong("id"));
                    v.setCandidateId(rs.getLong("candidate_id"));
                    v.setVersion(rs.getInt("version_no"));
                    v.setObjectRef(rs.getString("object_ref"));
                    v.setContentDigest(rs.getString("content_digest"));
                    v.setSizeBytes(rs.getLong("size_bytes"));
                    v.setMediaType(rs.getString("media_type"));
                    return v;
                }, candidateId, version);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("candidate version not found"));
    }

    public long createUploadSession(long candidateId, long teamId, long userId, String storageKey) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("upload_sessions")
                .usingGeneratedKeyColumns("id")
                .usingColumns("candidate_id", "team_id", "storage_key", "status", "created_by");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("candidate_id", candidateId).addValue("team_id", teamId)
                .addValue("storage_key", storageKey).addValue("status", "PENDING")
                .addValue("created_by", userId));
        return k.longValue();
    }

    public Optional<UploadSession> findUploadSession(long teamId, long id) {
        return jdbc.query("SELECT * FROM upload_sessions WHERE id = ? AND team_id = ?",
                (rs, n) -> {
                    UploadSession s = new UploadSession();
                    s.setId(rs.getLong("id"));
                    s.setCandidateId(rs.getLong("candidate_id"));
                    s.setTeamId(rs.getLong("team_id"));
                    s.setStorageKey(rs.getString("storage_key"));
                    s.setStatus(rs.getString("status"));
                    s.setExpectedSize((Long) rs.getObject("expected_size"));
                    s.setExpectedDigest(rs.getString("expected_digest"));
                    return s;
                }, id, teamId).stream().findFirst();
    }

    public UploadSession requireUploadSession(long teamId, long id) {
        return findUploadSession(teamId, id).orElseThrow(() -> ApiException.notFound("upload session not found"));
    }

    public void completeUploadSession(long id, long size, String digest) {
        jdbc.update("UPDATE upload_sessions SET status = 'COMPLETED', expected_size = ?, expected_digest = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                size, digest, id);
    }

    public void failUploadSession(long id) {
        jdbc.update("UPDATE upload_sessions SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
}