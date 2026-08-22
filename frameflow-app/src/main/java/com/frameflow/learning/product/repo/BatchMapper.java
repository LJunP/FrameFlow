package com.frameflow.learning.product.repo;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** generation_batches 表访问。 */
@Mapper
public interface BatchMapper {

    @Select("INSERT INTO generation_batches"
            + "(project_id, profile_version_id, brief_id, capacity, created_by) "
            + "VALUES(#{projectId}, #{profileVersionId}, #{briefId}, #{capacity}, #{createdBy}) RETURNING id")
    Long insert(@Param("projectId") Long projectId,
                @Param("profileVersionId") Long profileVersionId,
                @Param("briefId") Long briefId,
                @Param("capacity") int capacity,
                @Param("createdBy") Long createdBy);

    @Select("SELECT id, project_id, profile_version_id, brief_id, status, capacity, "
            + "created_by, created_at FROM generation_batches WHERE id = #{id}")
    BatchRow findById(Long id);

    @Update("UPDATE generation_batches SET status = 'CLOSED' WHERE id = #{id} AND status = 'OPEN'")
    int closeIfOpen(Long id);
}
