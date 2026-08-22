package com.frameflow.learning.product.repo;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** ranking_snapshots / ranking_entries 访问（只增不改：快照即固化）。 */
@Mapper
public interface RankingMapper {

    @Select("INSERT INTO ranking_snapshots"
            + "(batch_id, profile_version_id, algorithm_version, hamming_threshold, created_by) "
            + "VALUES(#{batchId}, #{profileVersionId}, #{algorithmVersion}, #{hammingThreshold}, "
            + "#{createdBy}) RETURNING id")
    Long insertSnapshot(@Param("batchId") Long batchId,
                        @Param("profileVersionId") Long profileVersionId,
                        @Param("algorithmVersion") String algorithmVersion,
                        @Param("hammingThreshold") int hammingThreshold,
                        @Param("createdBy") Long createdBy);

    @Insert("INSERT INTO ranking_entries"
            + "(snapshot_id, candidate_id, rank_no, cluster_id, is_representative, score, "
            + "breakdown, excluded_reason) "
            + "VALUES(#{snapshotId}, #{candidateId}, #{rankNo}, #{clusterId}, #{isRepresentative}, "
            + "#{score}, #{breakdown}::jsonb, #{excludedReason})")
    int insertEntry(@Param("snapshotId") Long snapshotId,
                    @Param("candidateId") Long candidateId,
                    @Param("rankNo") int rankNo,
                    @Param("clusterId") int clusterId,
                    @Param("isRepresentative") boolean isRepresentative,
                    @Param("score") int score,
                    @Param("breakdown") String breakdown,
                    @Param("excludedReason") String excludedReason);

    @Select("SELECT id FROM ranking_snapshots WHERE batch_id = #{batchId} "
            + "ORDER BY id DESC LIMIT 1")
    Long latestSnapshotId(Long batchId);

    @Select("SELECT id, batch_id, profile_version_id, algorithm_version, hamming_threshold, "
            + "created_by, created_at FROM ranking_snapshots WHERE id = #{id}")
    RankingSnapshotRow findSnapshot(Long id);

    @Select("SELECT id, snapshot_id, candidate_id, rank_no, cluster_id, is_representative, "
            + "score, breakdown::text AS breakdown, excluded_reason "
            + "FROM ranking_entries WHERE snapshot_id = #{snapshotId} ORDER BY rank_no, candidate_id")
    List<RankingEntryRow> entriesBySnapshot(Long snapshotId);
}
