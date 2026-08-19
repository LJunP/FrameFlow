package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.domain.Team;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TeamMapper {

    @Insert("INSERT INTO teams (name, created_by, created_at, updated_at) "
            + "VALUES (#{name}, #{createdBy}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Team team);

    @Select("SELECT id, name, created_by AS createdBy, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM teams WHERE id = #{id}")
    Team findById(@Param("id") long id);
}
