package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** candidates 表访问。 */
@Mapper
public interface CandidateMapper {

    @Select("INSERT INTO candidates"
            + "(batch_id, file_name, content_type, size_bytes, object_key, created_by) "
            + "VALUES(#{batchId}, #{fileName}, #{contentType}, #{sizeBytes}, #{objectKey}, #{createdBy}) "
            + "RETURNING id")
    Long insert(@Param("batchId") Long batchId,
                @Param("fileName") String fileName,
                @Param("contentType") String contentType,
                @Param("sizeBytes") long sizeBytes,
                @Param("objectKey") String objectKey,
                @Param("createdBy") Long createdBy);

    @Select("SELECT * FROM candidates WHERE id = #{id}")
    CandidateRow findById(Long id);

    @Select("SELECT * FROM candidates WHERE batch_id = #{batchId} ORDER BY id "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<CandidateRow> listByBatchPage(@Param("batchId") Long batchId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("SELECT count(*) FROM candidates WHERE batch_id = #{batchId} AND status <> 'INVALID'")
    int countActive(Long batchId);

    @Select("SELECT status, count(*) AS cnt FROM candidates WHERE batch_id = #{batchId} "
            + "GROUP BY status")
    List<StatusCount> countByStatus(Long batchId);

    /** 登记后回填上传会话信息（简单/分片 + S3 uploadId）。 */
    @Update("UPDATE candidates SET upload_mode = #{mode}, s3_upload_id = #{uploadId}, "
            + "updated_at = now() WHERE id = #{id}")
    int updateUploadSession(@Param("id") Long id,
                            @Param("mode") String mode,
                            @Param("uploadId") String uploadId);

    @Update("UPDATE candidates SET status = 'UPLOADED', etag = #{etag}, "
            + "uploaded_at = now(), updated_at = now() WHERE id = #{id}")
    int markUploaded(@Param("id") Long id, @Param("etag") String etag);

    // ★ 核心：坏文件判 INVALID 必须"证据留痕"——probe_error 写明原因，
    // 这是对"ANALYSIS_ERROR 不得伪装成视频不合格"红线的正向落实：
    // 每一条 INVALID 都能回答"当时为什么"。
    @Update("UPDATE candidates SET status = 'INVALID', probe_error = #{probeError}, "
            + "updated_at = now() WHERE id = #{id}")
    int markInvalid(@Param("id") Long id, @Param("probeError") String probeError);

    @Select("SELECT * FROM candidates WHERE batch_id = #{batchId} ORDER BY id")
    List<CandidateRow> listByBatch(Long batchId);

    @Update("UPDATE candidates SET status = 'INVALID', "
            + "probe_error = '上传会话超时未完成（对账判定）', updated_at = now() "
            + "WHERE batch_id = #{batchId} AND status = 'PENDING_UPLOAD' "
            + "AND created_at < #{cutoff}")
    int invalidateStalePending(@Param("batchId") Long batchId,
                               @Param("cutoff") OffsetDateTime cutoff);

    /** 分组计数的行形状。 */
    class StatusCount {
        private String status;
        private int cnt;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCnt() { return cnt; }
        public void setCnt(int cnt) { this.cnt = cnt; }
    }
}
