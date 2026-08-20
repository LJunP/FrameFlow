package com.frameflow.product.infrastructure.persistence;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.AnalysisSelection.AnalysisRun;
import com.frameflow.product.domain.AnalysisSelection.Finding;
import com.frameflow.product.domain.AnalysisSelection.RankingEntry;
import com.frameflow.product.domain.AnalysisSelection.RankingSnapshot;
import com.frameflow.product.domain.AnalysisSelection.SelectionSet;
import com.frameflow.product.domain.AnalysisSelection.SimilarityCluster;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepositories {

    private final JdbcTemplate jdbc;

    public AnalysisRepositories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- analysis runs ----
    private AnalysisRun mapRun(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        AnalysisRun r = new AnalysisRun();
        r.setId(rs.getLong("id"));
        r.setTeamId(rs.getLong("team_id"));
        r.setBatchId(rs.getLong("batch_id"));
        r.setCandidateVersionId(rs.getLong("candidate_version_id"));
        r.setStatus(rs.getString("status"));
        r.setCommandId(rs.getString("command_id"));
        r.setResultDigest(rs.getString("result_digest"));
        r.setDecision(rs.getString("decision"));
        r.setQualityVector(rs.getString("quality_vector"));
        r.setCreatedBy(rs.getLong("created_by"));
        return r;
    }

    public long createRun(long teamId, long batchId, long candidateVersionId, long userId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("analysis_runs")
                .usingGeneratedKeyColumns("id")
                .usingColumns("team_id", "batch_id", "candidate_version_id", "status", "created_by");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("team_id", teamId).addValue("batch_id", batchId)
                .addValue("candidate_version_id", candidateVersionId)
                .addValue("status", "CREATED").addValue("created_by", userId));
        return k.longValue();
    }

    public AnalysisRun requireRun(long teamId, long id) {
        List<AnalysisRun> rows = jdbc.query("SELECT * FROM analysis_runs WHERE id = ? AND team_id = ?",
                this::mapRun, id, teamId);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("analysis run not found"));
    }

    public AnalysisRun requireRunAnyTeam(long id) {
        List<AnalysisRun> rows = jdbc.query("SELECT * FROM analysis_runs WHERE id = ?",
                this::mapRun, id);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("analysis run not found"));
    }

    public List<AnalysisRun> listRunsByBatch(long teamId, long batchId) {
        return jdbc.query("SELECT * FROM analysis_runs WHERE team_id = ? AND batch_id = ? ORDER BY id",
                this::mapRun, teamId, batchId);
    }

    public void setRunStatus(long id, String status) {
        jdbc.update("UPDATE analysis_runs SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public void setRunStarted(long id, String commandId) {
        jdbc.update("UPDATE analysis_runs SET status = 'QUEUED', command_id = ?, started_at = COALESCE(started_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP WHERE id = ?", commandId, id);
    }

    public void completeRun(long id, String status, String resultDigest, String decision,
                            String qualityVector) {
        jdbc.update("UPDATE analysis_runs SET status = ?, result_digest = ?, decision = CAST(? AS jsonb), "
                        + "quality_vector = CAST(? AS jsonb), completed_at = CURRENT_TIMESTAMP, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, resultDigest, decision, qualityVector, id);
    }

    // ---- findings ----
    public void insertFinding(Finding f, long candidateId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("findings")
                .usingGeneratedKeyColumns("id")
                .usingColumns("analysis_run_id", "candidate_id", "rule_id", "rule_version",
                        "detector_id", "detector_version", "dimension", "finding_type",
                        "verdict", "severity", "confidence", "automation_action",
                        "start_ms", "end_ms", "summary", "evidence", "origin");
        insert.execute(new MapSqlParameterSource()
                .addValue("analysis_run_id", f.getAnalysisRunId())
                .addValue("candidate_id", candidateId)
                .addValue("rule_id", f.getRuleId())
                .addValue("rule_version", 1)
                .addValue("detector_id", f.getDetectorId())
                .addValue("detector_version", f.getDetectorVersion())
                .addValue("dimension", f.getDimension())
                .addValue("finding_type", f.getFindingType())
                .addValue("verdict", f.getVerdict())
                .addValue("severity", f.getSeverity())
                .addValue("confidence", f.getConfidence())
                .addValue("automation_action", f.getAutomationAction())
                .addValue("start_ms", f.getStartMs())
                .addValue("end_ms", f.getEndMs())
                .addValue("summary", f.getSummary())
                .addValue("evidence", Jsonb.pgOrDefault(f.getEvidence(), "{}"))
                .addValue("origin", Jsonb.pgOrDefault(f.getOrigin(), "{}")));
    }

    private Finding mapFinding(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Finding f = new Finding();
        f.setId(rs.getLong("id"));
        f.setAnalysisRunId(rs.getLong("analysis_run_id"));
        f.setCandidateId(rs.getLong("candidate_id"));
        f.setRuleId(rs.getString("rule_id"));
        f.setDetectorId(rs.getString("detector_id"));
        f.setDetectorVersion(rs.getString("detector_version"));
        f.setDimension(rs.getString("dimension"));
        f.setFindingType(rs.getString("finding_type"));
        f.setVerdict(rs.getString("verdict"));
        f.setSeverity(rs.getString("severity"));
        f.setConfidence(rs.getDouble("confidence"));
        f.setAutomationAction(rs.getString("automation_action"));
        f.setStartMs((Integer) rs.getObject("start_ms"));
        f.setEndMs((Integer) rs.getObject("end_ms"));
        f.setSummary(rs.getString("summary"));
        f.setEvidence(rs.getString("evidence"));
        f.setOrigin(rs.getString("origin"));
        return f;
    }

    public Long candidateIdOfRun(long runId) {
        return jdbc.queryForObject(
                "SELECT cv.candidate_id FROM analysis_runs ar "
                        + "JOIN candidate_versions cv ON cv.id = ar.candidate_version_id WHERE ar.id = ?",
                Long.class, runId);
    }

    public List<Finding> listFindingsByRun(long runId) {
        return jdbc.query("SELECT * FROM findings WHERE analysis_run_id = ? ORDER BY id", this::mapFinding, runId);
    }

    public List<Finding> listFindingsByCandidate(long candidateId) {
        return jdbc.query("SELECT * FROM findings WHERE candidate_id = ? ORDER BY id", this::mapFinding, candidateId);
    }

    // ---- ranking ----
    public int nextRankingVersion(long batchId) {
        Integer maxV = jdbc.queryForObject(
                "SELECT COALESCE(MAX(snapshot_version), 0) FROM ranking_snapshots WHERE batch_id = ?",
                Integer.class, batchId);
        return (maxV == null ? 0 : maxV) + 1;
    }

    public long insertRankingSnapshot(long batchId, int version, String algorithmVersion, String weights,
                                      String featureSchemaVersion, long userId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("ranking_snapshots")
                .usingGeneratedKeyColumns("id")
                .usingColumns("batch_id", "snapshot_version", "algorithm_version", "weights",
                        "feature_schema_version", "created_by");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("batch_id", batchId).addValue("snapshot_version", version)
                .addValue("algorithm_version", algorithmVersion)
                .addValue("weights", Jsonb.pgOrDefault(weights, "{}"))
                .addValue("feature_schema_version", featureSchemaVersion)
                .addValue("created_by", userId));
        return k.longValue();
    }

    public void insertRankingEntry(long snapshotId, long candidateId, int rank, double base, double finalScore,
                                   String breakdown) {
        jdbc.update("INSERT INTO ranking_entries (ranking_snapshot_id, candidate_id, rank, base_score, final_score, breakdown) VALUES (?,?,?,?,?,?)",
                snapshotId, candidateId, rank, base, finalScore, Jsonb.pgOrDefault(breakdown, "{}"));
    }

    public RankingSnapshot requireRankingAnyTeam(long id) {
        List<RankingSnapshot> rows = jdbc.query(
                "SELECT * FROM ranking_snapshots WHERE id = ?",
                (rs, n) -> {
                    RankingSnapshot s = new RankingSnapshot();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setSnapshotVersion(rs.getInt("snapshot_version"));
                    s.setAlgorithmVersion(rs.getString("algorithm_version"));
                    s.setWeights(rs.getString("weights"));
                    s.setFeatureSchemaVersion(rs.getString("feature_schema_version"));
                    s.setCreatedBy(rs.getLong("created_by"));
                    return s;
                }, id);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("ranking snapshot not found"));
    }

    public List<java.util.Map<String, Object>> listRankingSummaries(long teamId, long batchId) {
        return jdbc.query(
                "SELECT r.* FROM ranking_snapshots r JOIN batches b ON b.id = r.batch_id "
                        + "WHERE r.batch_id = ? AND b.team_id = ? ORDER BY r.id",
                (rs, n) -> {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("id", rs.getLong("id"));
                    m.put("batchId", rs.getLong("batch_id"));
                    m.put("snapshotVersion", rs.getInt("snapshot_version"));
                    m.put("algorithmVersion", rs.getString("algorithm_version"));
                    m.put("createdBy", rs.getLong("created_by"));
                    return m;
                }, batchId, teamId);
    }

    public long teamOfSelection(long selectionSetId) {
        Long teamId = jdbc.queryForObject(
                "SELECT b.team_id FROM selection_sets s JOIN batches b ON b.id = s.batch_id WHERE s.id = ?",
                Long.class, selectionSetId);
        if (teamId == null) {
            throw ApiException.notFound("selection set not found");
        }
        return teamId;
    }

    public RankingSnapshot requireRanking(long teamId, long id) {
        List<RankingSnapshot> rows = jdbc.query(
                "SELECT r.* FROM ranking_snapshots r JOIN batches b ON b.id = r.batch_id WHERE r.id = ? AND b.team_id = ?",
                (rs, n) -> {
                    RankingSnapshot s = new RankingSnapshot();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setSnapshotVersion(rs.getInt("snapshot_version"));
                    s.setAlgorithmVersion(rs.getString("algorithm_version"));
                    s.setWeights(rs.getString("weights"));
                    s.setFeatureSchemaVersion(rs.getString("feature_schema_version"));
                    s.setCreatedBy(rs.getLong("created_by"));
                    return s;
                }, id, teamId);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("ranking snapshot not found"));
    }

    public List<RankingEntry> entriesOf(long snapshotId) {
        return jdbc.query("SELECT * FROM ranking_entries WHERE ranking_snapshot_id = ? ORDER BY rank",
                (rs, n) -> {
                    RankingEntry e = new RankingEntry();
                    e.setRankingSnapshotId(rs.getLong("ranking_snapshot_id"));
                    e.setCandidateId(rs.getLong("candidate_id"));
                    e.setRank(rs.getInt("rank"));
                    e.setBaseScore(rs.getDouble("base_score"));
                    e.setFinalScore(rs.getDouble("final_score"));
                    e.setBreakdown(rs.getString("breakdown"));
                    return e;
                }, snapshotId);
    }

    // ---- similarity clusters ----
    public long insertCluster(long batchId, String clusterKey, double threshold, String version,
                              Long representative) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("similarity_clusters")
                .usingGeneratedKeyColumns("id")
                .usingColumns("batch_id", "cluster_key", "threshold", "algorithm_version",
                        "representative_candidate_id");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("batch_id", batchId).addValue("cluster_key", clusterKey)
                .addValue("threshold", threshold).addValue("algorithm_version", version)
                .addValue("representative_candidate_id", representative));
        return k.longValue();
    }

    public void insertClusterMember(long clusterId, long candidateId, double similarity) {
        jdbc.update("INSERT INTO cluster_members (cluster_id, candidate_id, similarity) VALUES (?,?,?)",
                clusterId, candidateId, similarity);
    }

    public List<SimilarityCluster> listClusters(long teamId, long batchId) {
        return jdbc.query(
                "SELECT c.* FROM similarity_clusters c JOIN batches b ON b.id = c.batch_id WHERE c.batch_id = ? AND b.team_id = ? ORDER BY c.id",
                (rs, n) -> {
                    SimilarityCluster c = new SimilarityCluster();
                    c.setId(rs.getLong("id"));
                    c.setBatchId(rs.getLong("batch_id"));
                    c.setClusterKey(rs.getString("cluster_key"));
                    c.setThreshold(rs.getDouble("threshold"));
                    c.setAlgorithmVersion(rs.getString("algorithm_version"));
                    Object rep = rs.getObject("representative_candidate_id");
                    c.setRepresentativeCandidateId(rep == null ? null : rs.getLong("representative_candidate_id"));
                    return c;
                }, batchId, teamId);
    }

    // ---- selection sets ----
    public long createSelectionSet(long batchId, String name, int topK, long userId) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc).withTableName("selection_sets")
                .usingGeneratedKeyColumns("id")
                .usingColumns("batch_id", "name", "status", "top_k", "created_by");
        Number k = insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("batch_id", batchId).addValue("name", name)
                .addValue("status", "DRAFT").addValue("top_k", topK)
                .addValue("created_by", userId));
        return k.longValue();
    }

    public void insertSelectionItem(long setId, long candidateId, int seq) {
        jdbc.update("INSERT INTO selection_items (selection_set_id, candidate_id, seq) VALUES (?,?,?)",
                setId, candidateId, seq);
    }

    public SelectionSet requireSelectionSetAnyTeam(long id) {
        List<SelectionSet> rows = jdbc.query(
                "SELECT * FROM selection_sets WHERE id = ?",
                (rs, n) -> {
                    SelectionSet s = new SelectionSet();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setName(rs.getString("name"));
                    s.setStatus(rs.getString("status"));
                    s.setTopK(rs.getInt("top_k"));
                    s.setTheme(rs.getString("theme"));
                    s.setLockedBy((Long) rs.getObject("locked_by"));
                    s.setCreatedBy(rs.getLong("created_by"));
                    return s;
                }, id);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("selection set not found"));
    }

    public SelectionSet requireSelectionSet(long teamId, long id) {
        List<SelectionSet> rows = jdbc.query(
                "SELECT s.* FROM selection_sets s JOIN batches b ON b.id = s.batch_id WHERE s.id = ? AND b.team_id = ?",
                (rs, n) -> {
                    SelectionSet s = new SelectionSet();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setName(rs.getString("name"));
                    s.setStatus(rs.getString("status"));
                    s.setTopK(rs.getInt("top_k"));
                    s.setTheme(rs.getString("theme"));
                    s.setLockedBy((Long) rs.getObject("locked_by"));
                    s.setCreatedBy(rs.getLong("created_by"));
                    return s;
                }, id, teamId);
        return rows.stream().findFirst().orElseThrow(() -> ApiException.notFound("selection set not found"));
    }

    public List<SelectionSet> listSelectionSets(long teamId, long batchId) {
        return jdbc.query(
                "SELECT s.* FROM selection_sets s JOIN batches b ON b.id = s.batch_id WHERE s.batch_id = ? AND b.team_id = ? ORDER BY s.id",
                (rs, n) -> {
                    SelectionSet s = new SelectionSet();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setName(rs.getString("name"));
                    s.setStatus(rs.getString("status"));
                    s.setTopK(rs.getInt("top_k"));
                    s.setTheme(rs.getString("theme"));
                    s.setLockedBy((Long) rs.getObject("locked_by"));
                    s.setCreatedBy(rs.getLong("created_by"));
                    return s;
                }, batchId, teamId);
    }

    public List<Long> selectionItemCandidateIds(long setId) {
        return jdbc.query("SELECT candidate_id FROM selection_items WHERE selection_set_id = ? ORDER BY seq",
                (rs, n) -> rs.getLong("candidate_id"), setId);
    }

    public void lockSelectionSet(long id, long userId) {
        jdbc.update("UPDATE selection_sets SET status = 'LOCKED', locked_by = ?, locked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                userId, id);
    }

    public void supersedeSelectionSet(long id) {
        jdbc.update("UPDATE selection_sets SET status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
}