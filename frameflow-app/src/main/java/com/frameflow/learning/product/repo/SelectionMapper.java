package com.frameflow.learning.product.repo;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/** selection_sets / selection_items 访问。 */
@Mapper
public interface SelectionMapper {

    @Select("INSERT INTO selection_sets(batch_id, snapshot_id, top_k, created_by) "
            + "VALUES(#{batchId}, #{snapshotId}, #{topK}, #{createdBy}) RETURNING id")
    Long insertSet(@Param("batchId") Long batchId,
                   @Param("snapshotId") Long snapshotId,
                   @Param("topK") int topK,
                   @Param("createdBy") Long createdBy);

    @Select("SELECT id, batch_id, snapshot_id, status, top_k, created_by, created_at, "
            + "locked_at, locked_by FROM selection_sets WHERE id = #{id}")
    SelectionRow findSet(Long id);

    @Select("SELECT id, batch_id, snapshot_id, status, top_k, created_by, created_at, "
            + "locked_at, locked_by FROM selection_sets WHERE batch_id = #{batchId} ORDER BY id")
    List<SelectionRow> listByBatch(Long batchId);

    @Insert("INSERT INTO selection_items(selection_id, candidate_id, machine_pick, "
            + "human_action, note) VALUES(#{selectionId}, #{candidateId}, #{machinePick}, "
            + "#{humanAction}, #{note})")
    int insertItem(@Param("selectionId") Long selectionId,
                   @Param("candidateId") Long candidateId,
                   @Param("machinePick") boolean machinePick,
                   @Param("humanAction") String humanAction,
                   @Param("note") String note);

    /**
     * ★ 核心：人工叠加调整——只更新 human_action/note，绝不动 machine_pick；
     * 且仅在 DRAFT 态可写（JOIN 条件挡住已锁定的优选集）。
     */
    @Update("UPDATE selection_items si SET human_action = #{action}, note = #{note} "
            + "WHERE si.selection_id = #{selectionId} AND si.candidate_id = #{candidateId} "
            + "AND EXISTS (SELECT 1 FROM selection_sets s "
            + "WHERE s.id = si.selection_id AND s.status = 'DRAFT')")
    int adjustItem(@Param("selectionId") Long selectionId,
                   @Param("candidateId") Long candidateId,
                   @Param("action") String action,
                   @Param("note") String note);

    /** 同上：新条目也只在 DRAFT 态可插入。 */
    @Insert("INSERT INTO selection_items(selection_id, candidate_id, machine_pick, "
            + "human_action, note) "
            + "SELECT #{selectionId}, #{candidateId}, false, #{action}, #{note} "
            + "WHERE EXISTS (SELECT 1 FROM selection_sets s "
            + "WHERE s.id = #{selectionId} AND s.status = 'DRAFT') "
            + "ON CONFLICT DO NOTHING")
    int addItemIfDraft(@Param("selectionId") Long selectionId,
                       @Param("candidateId") Long candidateId,
                       @Param("action") String action,
                       @Param("note") String note);

    @Select("SELECT si.id, si.selection_id, si.candidate_id, si.machine_pick, "
            + "si.human_action, si.note FROM selection_items si "
            + "WHERE si.selection_id = #{selectionId} ORDER BY si.candidate_id")
    List<SelectionItemRow> itemsBySelection(Long selectionId);

    /**
     * ★ 核心：导出用"一次 JOIN 取齐"——优选项 × 候选元数据 × 排名分数，
     * 绝不先查 items 再逐条回查 candidates/ranking（那是 N+1，导出会随
     * 条目数线性放大往返次数）。LEFT JOIN ranking_entries：人工 INCLUDE 的
     * 候选可能根本不在快照里，必须保留为 rank=0，不能因 JOIN 缺失被丢掉。
     * 排序 COALESCE(rank_no,0) 与旧导出（未入榜记 0 排在最前）逐字一致。
     */
    @Select("SELECT si.candidate_id, si.machine_pick, re.rank_no, re.score AS composite_score, "
            + "re.cluster_id, si.human_action, c.file_name, c.status, c.size_bytes, c.content_type, "
            + "c.uploaded_at "
            + "FROM selection_items si "
            + "JOIN candidates c ON c.id = si.candidate_id "
            + "LEFT JOIN ranking_entries re ON re.snapshot_id = #{snapshotId} "
            + "AND re.candidate_id = si.candidate_id "
            + "WHERE si.selection_id = #{selectionId} "
            + "ORDER BY COALESCE(re.rank_no, 0), si.candidate_id")
    List<ExportItemRow> exportItems(@Param("selectionId") Long selectionId,
                                    @Param("snapshotId") Long snapshotId);

    // ★ 核心：锁定是条件更新——并发/重复锁定只有一个成功，天然幂等防重入
    @Update("UPDATE selection_sets SET status = 'LOCKED', locked_at = now(), "
            + "locked_by = #{userId} WHERE id = #{id} AND status = 'DRAFT'")
    int lockIfDraft(@Param("id") Long id, @Param("userId") Long userId);

    /** 行对象。 */
    class SelectionItemRow {
        private Long id;
        private Long selectionId;
        private Long candidateId;
        private boolean machinePick;
        private String humanAction;
        private String note;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSelectionId() { return selectionId; }
        public void setSelectionId(Long selectionId) { this.selectionId = selectionId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public boolean isMachinePick() { return machinePick; }
        public void setMachinePick(boolean machinePick) { this.machinePick = machinePick; }
        public String getHumanAction() { return humanAction; }
        public void setHumanAction(String humanAction) { this.humanAction = humanAction; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    /** 导出行的 JOIN 形状：优选项 + 候选元数据 + 排名分数（可空）。 */
    class ExportItemRow {
        private Long candidateId;
        /** 机器是否入选 Top-K：与人工动作并存，是 F7 可审计性的关键字段。 */
        private boolean machinePick;
        private Integer rankNo;
        private Integer compositeScore;
        private Integer clusterId;
        private String humanAction;
        private String fileName;
        private String status;
        private Long sizeBytes;
        private String contentType;
        private OffsetDateTime uploadedAt;

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public boolean isMachinePick() { return machinePick; }
        public void setMachinePick(boolean machinePick) { this.machinePick = machinePick; }
        public Integer getRankNo() { return rankNo; }
        public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
        public Integer getCompositeScore() { return compositeScore; }
        public void setCompositeScore(Integer compositeScore) { this.compositeScore = compositeScore; }
        public Integer getClusterId() { return clusterId; }
        public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
        public String getHumanAction() { return humanAction; }
        public void setHumanAction(String humanAction) { this.humanAction = humanAction; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public OffsetDateTime getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}
