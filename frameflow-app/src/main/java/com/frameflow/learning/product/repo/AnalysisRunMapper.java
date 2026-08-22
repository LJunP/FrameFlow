package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** analysis_runs 表访问。 */
@Mapper
public interface AnalysisRunMapper {

    @Select("INSERT INTO analysis_runs(candidate_id, batch_id) "
            + "VALUES(#{candidateId}, #{batchId}) RETURNING id")
    Long insert(@Param("candidateId") Long candidateId, @Param("batchId") Long batchId);

    @Select("SELECT id, candidate_id, batch_id, worker_version, status, error_summary, "
            + "started_at, finished_at FROM analysis_runs WHERE id = #{id}")
    AnalysisRunRow findById(Long id);

    /**
     * ★ 核心：幂等回写的唯一裁决点——条件更新只在 RUNNING 时生效。
     * 两个相同结果并发到达（或重放），只有一个 UPDATE 命中（返回 1），
     * 另一个返回 0 并被判定为"重复结果"，直接返回既有状态，不再插入
     * 任何 Finding。状态迁移本身就是互斥锁，不需要额外的去重表。
     */
    @Update("UPDATE analysis_runs SET status = #{status}, worker_version = #{workerVersion}, "
            + "error_summary = #{errorSummary}, finished_at = now() "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int finalizeIfRunning(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("workerVersion") String workerVersion,
                          @Param("errorSummary") String errorSummary);
}
