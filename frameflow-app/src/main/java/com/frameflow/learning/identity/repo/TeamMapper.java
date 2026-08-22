package com.frameflow.learning.identity.repo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** teams 表访问。 */
@Mapper
public interface TeamMapper {

    @Select("INSERT INTO teams(name) VALUES(#{name}) RETURNING id")
    Long insert(@Param("name") String name);

    @Select("SELECT id, name FROM teams WHERE id = #{id}")
    TeamRow findById(Long id);
}
