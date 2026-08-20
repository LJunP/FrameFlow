package com.frameflow.product.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.product.api.ApiException;
import com.frameflow.product.infrastructure.persistence.AnalysisRepositories;
import com.frameflow.product.infrastructure.persistence.BatchRepositories;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Clustering, ranking and selection for a finished batch (S6). */
@Service
public class SelectionService {

    private final AnalysisRepositories repos;
    private final BatchRepositories batchRepos;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SelectionService(AnalysisRepositories repos, BatchRepositories batchRepos,
                            JdbcTemplate jdbc, ObjectMapper mapper) {
        this.repos = repos;
        this.batchRepos = batchRepos;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public List<com.frameflow.product.domain.AnalysisSelection.SimilarityCluster>
        clusterBatch(long teamId, long batchId, double threshold) {
        batchRepos.require(teamId, batchId);
        Map<Long, String> digests = new TreeMap<>();
        jdbc.query("SELECT c.id, cv.content_digest FROM candidates c "
                + "JOIN candidate_versions cv ON cv.candidate_id = c.id "
                + "WHERE c.batch_id = ? AND c.team_id = ?",
                rs -> {
                    long candidateId = rs.getLong("id");
                    String digest = rs.getString("content_digest");
                    if (digest != null) {
                        digests.put(candidateId, digest);
                    }
                }, batchId, teamId);
        Map<String, List<Long>> groups = new LinkedHashMap<>();
        digests.forEach((cand, digest) ->
                groups.computeIfAbsent(digest, k -> new java.util.ArrayList<>()).add(cand));
        int n = 0;
        for (Map.Entry<String, List<Long>> grp : groups.entrySet()) {
            n++;
            String key = "cluster-" + n;
            long rep = grp.getValue().get(0);
            long clusterId = repos.insertCluster(batchId, key, threshold, "1.0.0", rep);
            for (Long cand : grp.getValue()) {
                repos.insertClusterMember(clusterId, cand, 1.0);
            }
        }
        return repos.listClusters(teamId, batchId);
    }

    @Transactional
    public com.frameflow.product.domain.AnalysisSelection.RankingSnapshot
        rankBatch(long teamId, long batchId, long userId) {
        batchRepos.require(teamId, batchId);
        int version = repos.nextRankingVersion(batchId);
        Map<String, Double> defaultWeights = new TreeMap<>();
        defaultWeights.put("prompt_alignment", 0.25);
        defaultWeights.put("product_visibility", 0.20);
        defaultWeights.put("visual_stability", 0.15);
        defaultWeights.put("temporal_consistency", 0.15);
        defaultWeights.put("technical_quality", 0.10);
        defaultWeights.put("brand_compliance", 0.10);
        defaultWeights.put("audio_subtitle_quality", 0.05);
        String weightsJson = "{}";
        try {
            weightsJson = mapper.writeValueAsString(defaultWeights);
        } catch (Exception ignored) {
        }
        long snapshotId = repos.insertRankingSnapshot(batchId, version, "weighted-linear-1.0.0",
                weightsJson, "1.0.0", userId);
        List<Long> eligible = jdbc.query(
                "SELECT c.id FROM candidates c WHERE c.batch_id = ? AND c.team_id = ? "
                        + "AND c.status IN ('ANALYZED')",
                (rs, n) -> rs.getLong("id"), batchId, teamId);
        List<Map.Entry<Long, Double>> scored = new java.util.ArrayList<>();
        for (Long cand : eligible) {
            double score = scoreForCandidate(cand);
            scored.add(Map.entry(cand, score));
        }
        scored.sort((a, b) -> {
            int c = Double.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Long.compare(a.getKey(), b.getKey());
        });
        int rank = 1;
        for (Map.Entry<Long, Double> en : scored) {
            repos.insertRankingEntry(snapshotId, en.getKey(), rank++, en.getValue(), en.getValue(), "{}");
        }
        return repos.requireRanking(teamId, snapshotId);
    }

    private double scoreForCandidate(long candidateId) {
        // Deterministic heuristic from violations: fewer BLOCKER/MAJOR violations => higher.
        Integer blockers = jdbc.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ? AND severity IN ('BLOCKER')",
                Integer.class, candidateId);
        Integer majors = jdbc.queryForObject(
                "SELECT count(*) FROM findings WHERE candidate_id = ? AND severity IN ('MAJOR')",
                Integer.class, candidateId);
        int b = blockers == null ? 0 : blockers;
        int m = majors == null ? 0 : majors;
        return Math.max(0.0, 1.0 - b * 0.5 - m * 0.15);
    }

    public com.frameflow.product.domain.AnalysisSelection.RankingSnapshot getRanking(long teamId, long id) {
        return repos.requireRanking(teamId, id);
    }

    public com.frameflow.product.domain.AnalysisSelection.RankingSnapshot getRanking(long id) {
        return repos.requireRankingAnyTeam(id);
    }

    public List<com.frameflow.product.domain.AnalysisSelection.RankingEntry> rankingEntries(long teamId, long id) {
        repos.requireRanking(teamId, id);
        return repos.entriesOf(id);
    }

    public List<com.frameflow.product.domain.AnalysisSelection.SimilarityCluster> listClusters(long teamId, long batchId) {
        return repos.listClusters(teamId, batchId);
    }

    public List<java.util.Map<String, Object>> listRankings(long teamId, long batchId) {
        return repos.listRankingSummaries(teamId, batchId);
    }

    public long batchTeamOfSelection(long selectionSetId) {
        return repos.teamOfSelection(selectionSetId);
    }

    @Transactional
    public com.frameflow.product.domain.AnalysisSelection.SelectionSet
        createSelection(long teamId, long batchId, long userId, String name, int topK) {
        batchRepos.require(teamId, batchId);
        long id = repos.createSelectionSet(batchId, name, topK, userId);
        Integer latest = jdbc.queryForObject(
                "SELECT COALESCE(MAX(snapshot_version),0) FROM ranking_snapshots WHERE batch_id = ?",
                Integer.class, batchId);
        Long snapshotId = null;
        if (latest != null && latest > 0) {
            snapshotId = jdbc.queryForObject(
                    "SELECT id FROM ranking_snapshots WHERE batch_id = ? AND snapshot_version = ?",
                    Long.class, batchId, latest);
        }
        if (snapshotId != null) {
            var top = repos.entriesOf(snapshotId).stream().limit(topK).toList();
            int seq = 1;
            for (var en : top) {
                repos.insertSelectionItem(id, en.getCandidateId(), seq++);
            }
        }
        return repos.requireSelectionSet(teamId, id);
    }

    public List<com.frameflow.product.domain.AnalysisSelection.SelectionSet> listSelections(long teamId, long batchId) {
        return repos.listSelectionSets(teamId, batchId);
    }

    public com.frameflow.product.domain.AnalysisSelection.SelectionSet getSelection(long teamId, long id) {
        return repos.requireSelectionSet(teamId, id);
    }

    public com.frameflow.product.domain.AnalysisSelection.SelectionSet getSelection(long id) {
        return repos.requireSelectionSetAnyTeam(id);
    }

    public List<Long> selectionCandidateIds(long teamId, long id) {
        repos.requireSelectionSet(teamId, id);
        return repos.selectionItemCandidateIds(id);
    }

    @Transactional
    public com.frameflow.product.domain.AnalysisSelection.SelectionSet lockSelection(long teamId, long id, long userId) {
        var s = repos.requireSelectionSet(teamId, id);
        if ("LOCKED".equals(s.getStatus())) {
            throw ApiException.conflict("SELECTION_ALREADY_LOCKED", "selection set already locked");
        }
        repos.lockSelectionSet(id, userId);
        return repos.requireSelectionSet(teamId, id);
    }
}
