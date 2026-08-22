package com.frameflow.learning.product.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frameflow.learning.product.repo.CandidateMapper;
import com.frameflow.learning.product.repo.CandidateRow;
import com.frameflow.learning.product.repo.FindingMapper;
import com.frameflow.learning.product.repo.FindingMapper.FindingRow;
import com.frameflow.learning.product.repo.QualityProfileMapper;
import com.frameflow.learning.product.repo.QualityProfileVersionRow;
import com.frameflow.learning.product.repo.RankingEntryRow;
import com.frameflow.learning.product.repo.RankingMapper;
import com.frameflow.learning.product.repo.RankingSnapshotRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排名：资格门 → 加权评分（可解释）→ 重复聚类 → 确定性排序 → 快照固化。
 *
 * 三个不变量（docs/01 §9）：
 * 1. 只有确定性 BLOCKER 能把候选挡在门外（资格门看状态，语义只影响复核）；
 * 2. 排名可复现：同输入同输出（分数并列时按 candidateId 决胜，杜绝随机）；
 * 3. 快照固化：分数与聚类一旦写入永不修改，事后任何重排都是新快照。
 */
@Service
public class RankingService {

    private static final String ALGORITHM_VERSION = "rank-v1";
    private static final int DEFAULT_WEIGHT = 10;
    private static final int DEFAULT_HAMMING_THRESHOLD = 6;

    private final BatchService batchService;
    private final CandidateMapper candidates;
    private final FindingMapper findings;
    private final QualityProfileMapper profiles;
    private final RankingMapper rankings;
    private final ObjectMapper objectMapper;

    public RankingService(BatchService batchService, CandidateMapper candidates,
                          FindingMapper findings, QualityProfileMapper profiles,
                          RankingMapper rankings, ObjectMapper objectMapper) {
        this.batchService = batchService;
        this.candidates = candidates;
        this.findings = findings;
        this.profiles = profiles;
        this.rankings = rankings;
        this.objectMapper = objectMapper;
    }

    public record RankSummary(long snapshotId, int ranked, int clusters, int excluded) {
    }

    @Transactional
    public RankSummary rank(long userId, long batchId) {
        var batch = batchService.requireWritableBatch(userId, batchId);
        QualityProfileVersionRow version =
                profiles.findVersionById(batch.getProfileVersionId());
        JsonNode spec = parse(version.getSpecJson());
        Map<String, Integer> weights = readWeights(spec);
        int hammingThreshold = spec.path("duplicates").path("hammingThreshold")
                .asInt(DEFAULT_HAMMING_THRESHOLD);

        // ---------- 1. 资格门 + 评分 ----------
        List<Scored> scoredList = new ArrayList<>();
        List<CandidateRow> excludedList = new ArrayList<>();
        for (CandidateRow candidate : candidates.listByBatch(batchId)) {
            // ★ 资格门：只有 ANALYZED / REVIEW_REQUIRED 可参选。
            // AUTO_REJECT（确定性 BLOCKER）与 ANALYSIS_ERROR/INVALID 出局——
            // 但出局也要留痕（excluded_reason），排名不是无声淘汰。
            String status = candidate.getStatus();
            if (!"ANALYZED".equals(status) && !"REVIEW_REQUIRED".equals(status)) {
                excludedList.add(candidate);
                continue;
            }
            scoredList.add(score(candidate, weights));
        }

        // ---------- 2. 聚类（并查集：精确 + 近重复） ----------
        UnionFind uf = new UnionFind(scoredList.size());
        for (int i = 0; i < scoredList.size(); i++) {
            for (int j = i + 1; j < scoredList.size(); j++) {
                if (isDuplicate(scoredList.get(i), scoredList.get(j), hammingThreshold)) {
                    uf.union(i, j);
                }
            }
        }
        // 稳定簇编号：按"簇代表的 candidateId 升序"编号——代表是簇内
        // (score desc, candidateId asc) 的最优者，簇编号因此可复现
        Map<Integer, Integer> clusterRepresentative = new HashMap<>();
        for (int i = 0; i < scoredList.size(); i++) {
            int root = uf.find(i);
            Scored current = scoredList.get(i);
            Scored best = clusterRepresentative.containsKey(root)
                    ? scoredList.get(clusterRepresentative.get(root)) : null;
            if (best == null || current.score > best.score
                    || (current.score == best.score
                        && current.candidate().getId() < best.candidate().getId())) {
                clusterRepresentative.put(root, i);
            }
        }
        List<Integer> rootsByRepId = clusterRepresentative.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        scoredList.get(a.getValue()).candidate().getId(),
                        scoredList.get(b.getValue()).candidate().getId()))
                .map(Map.Entry::getKey)
                .toList();
        Map<Integer, Integer> clusterNo = new HashMap<>();
        for (int no = 0; no < rootsByRepId.size(); no++) {
            clusterNo.put(rootsByRepId.get(no), no + 1);
        }

        // ---------- 3. 排名（仅代表参排；确定性并列决胜） ----------
        List<Scored> representatives = new ArrayList<>();
        for (int i = 0; i < scoredList.size(); i++) {
            if (clusterRepresentative.get(uf.find(i)) == i) {
                representatives.add(scoredList.get(i));
            }
        }
        representatives.sort((a, b) -> {
            int byScore = Integer.compare(b.score, a.score);
            return byScore != 0 ? byScore
                    : Long.compare(a.candidate().getId(), b.candidate().getId());
        });

        // ---------- 4. 快照固化 ----------
        long snapshotId = rankings.insertSnapshot(batchId, batch.getProfileVersionId(),
                ALGORITHM_VERSION, hammingThreshold, userId);
        Map<Long, Integer> rankByCandidate = new HashMap<>();
        for (int r = 0; r < representatives.size(); r++) {
            rankByCandidate.put(representatives.get(r).candidate().getId(), r + 1);
        }
        Map<Long, Long> repIdByCandidate = new HashMap<>();
        for (int i = 0; i < scoredList.size(); i++) {
            long repId = scoredList.get(clusterRepresentative.get(uf.find(i)))
                    .candidate().getId();
            repIdByCandidate.put(scoredList.get(i).candidate().getId(), repId);
        }
        for (Scored s : scoredList) {
            boolean isRep = rankByCandidate.containsKey(s.candidate().getId());
            String reason = isRep ? null
                    : "DUPLICATE_OF_" + repIdByCandidate.get(s.candidate().getId());
            rankings.insertEntry(snapshotId, s.candidate().getId(),
                    isRep ? rankByCandidate.get(s.candidate().getId()) : 0,
                    clusterNo.get(uf.find(indexOf(scoredList, s))),
                    isRep, s.score, s.breakdown.toString(), reason);
        }
        for (CandidateRow ex : excludedList) {
            rankings.insertEntry(snapshotId, ex.getId(), 0, 0, false, 0,
                    "{\"deductions\":[]}", "STATUS_" + ex.getStatus());
        }
        return new RankSummary(snapshotId, representatives.size(),
                rootsByRepId.size(), excludedList.size());
    }

    /** 视图方法：repo 行 → web DTO（映射留在服务层，Controller 零依赖 repo）。 */
    public com.frameflow.learning.product.web.RankingDtos.LatestRankingResponse latestView(
            long userId, long batchId) {
        RankingSnapshotRow snapshot = latestSnapshot(userId, batchId);
        if (snapshot == null) {
            throw new com.frameflow.learning.shared.error.ApiException(
                    com.frameflow.learning.shared.error.ErrorCode.RESOURCE_NOT_FOUND,
                    "该批次还没有排名快照");
        }
        return new com.frameflow.learning.product.web.RankingDtos.LatestRankingResponse(
                snapshot.getId(), snapshot.getAlgorithmVersion(), snapshot.getHammingThreshold(),
                entries(snapshot.getId()).stream().map(e ->
                        new com.frameflow.learning.product.web.RankingDtos.RankingEntryResponse(
                                e.getCandidateId(), e.getRankNo(), e.getClusterId(),
                                e.isRepresentative(), e.getScore(), e.getBreakdown(),
                                e.getExcludedReason())).toList());
    }

    public RankingSnapshotRow latestSnapshot(long userId, long batchId) {
        batchService.requireBatchOfMyTeam(userId, batchId);
        Long id = rankings.latestSnapshotId(batchId);
        return id == null ? null : rankings.findSnapshot(id);
    }

    public List<RankingEntryRow> entries(long snapshotId) {
        return rankings.entriesBySnapshot(snapshotId);
    }

    // ---------- 内部 ----------

    /** 单候选评分：100 起步，每个未通过项扣其权重；扣减明细全部留档。 */
    private Scored score(CandidateRow candidate, Map<String, Integer> weights) {
        int score = 100;
        ArrayNode deductions = objectMapper.createArrayNode();
        for (FindingRow f : findings.listByCandidate(candidate.getId())) {
            if (f.isPassed() || "BLOCKER".equals(f.getSeverity())) {
                continue;   // 通过项不扣分；BLOCKER 已被资格门挡住，不会出现在这
            }
            boolean unknownSemantic = f.getVerdict() != null && "UNKNOWN".equals(f.getVerdict());
            if (unknownSemantic) {
                continue;   // ★ 模型"不确定"不是候选的错：不扣分，交给人工复核
            }
            int weight = weights.getOrDefault(f.getDimension(), DEFAULT_WEIGHT);
            score -= weight;
            ObjectNode d = deductions.addObject();
            d.put("dimension", f.getDimension());
            d.put("weight", weight);
            if (f.getVerdict() != null) {
                d.put("verdict", f.getVerdict());
            }
        }
        ObjectNode breakdown = objectMapper.createObjectNode();
        breakdown.set("deductions", deductions);
        breakdown.put("finalScore", Math.max(score, 0));
        return new Scored(candidate, Math.max(score, 0), breakdown);
    }

    /** 精确（同 content_hash）或近重复（phash 海明距离 ≤ 阈值）。 */
    private boolean isDuplicate(Scored a, Scored b, int threshold) {
        String ha = a.candidate().getContentHash();
        String hb = b.candidate().getContentHash();
        if (ha != null && ha.equals(hb)) {
            return true;
        }
        String pa = a.candidate().getPhash();
        String pb = b.candidate().getPhash();
        if (pa == null || pb == null) {
            return false;
        }
        long diff = Long.bitCount(Long.parseUnsignedLong(pa, 16)
                ^ Long.parseUnsignedLong(pb, 16));
        return diff <= threshold;
    }

    private Map<String, Integer> readWeights(JsonNode spec) {
        Map<String, Integer> weights = new HashMap<>();
        spec.path("weights").fields()
                .forEachRemaining(e -> weights.put(e.getKey(), e.getValue().asInt(DEFAULT_WEIGHT)));
        return weights;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static int indexOf(List<Scored> list, Scored target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /** 并查集（union-find）：近重复聚类的标准姿势——加边即并集。 */
    static class UnionFind {
        private final int[] parent;

        UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];   // 路径减半
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            parent[find(a)] = find(b);
        }
    }

    record Scored(CandidateRow candidate, int score, ObjectNode breakdown) {
    }
}
