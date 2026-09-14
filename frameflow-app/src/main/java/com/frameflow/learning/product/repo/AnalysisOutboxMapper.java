package com.frameflow.learning.product.repo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AnalysisOutboxMapper {

    @Insert("INSERT INTO analysis_outbox(run_id, payload) VALUES(#{runId}, #{payload})")
    int insert(@Param("runId") long runId, @Param("payload") String payload);

    @Select("SELECT id, run_id AS runId, payload FROM analysis_outbox "
            + "WHERE published_at IS NULL ORDER BY id LIMIT #{limit}")
    List<OutboxRow> listUnpublished(int limit);

    @Update("UPDATE analysis_outbox SET published_at = #{publishedAt} "
            + "WHERE id = #{id} AND published_at IS NULL")
    int markPublished(@Param("id") long id, @Param("publishedAt") OffsetDateTime publishedAt);

    class OutboxRow {
        private long id;
        private long runId;
        private String payload;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getRunId() { return runId; }
        public void setRunId(long runId) { this.runId = runId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
    }
}
