package com.frameflow.learning.identity.repo;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * users 表访问。SQL 全部手写——这是本项目的一贯原则（docs/02 技术栈），
 * 好处是每一条进数据库的语句都看得见、可审查、可控索引。
 */
@Mapper
public interface UserMapper {

    // ★ 核心：PostgreSQL 的 INSERT ... RETURNING 让插入和新 id 一次往返拿到，
    // 替代"先 INSERT 再 SELECT lastval"的两步写法（两步在并发下不可靠）。
    // 注意这是 PG 特有语法，换数据库（如 MySQL）需改用自增主键回填。
    @Select("INSERT INTO users(email, password_hash, display_name) "
            + "VALUES(#{email}, #{passwordHash}, #{displayName}) RETURNING id")
    Long insert(@Param("email") String email,
                @Param("passwordHash") String passwordHash,
                @Param("displayName") String displayName);

    @Select("SELECT id, email, password_hash, display_name, status, created_at "
            + "FROM users WHERE email = #{email}")
    UserRow findByEmail(String email);

    @Select("SELECT id, email, password_hash, display_name, status, created_at "
            + "FROM users WHERE id = #{id}")
    UserRow findById(Long id);

    @Update("UPDATE users SET display_name = #{displayName} WHERE id = #{id}")
    int updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    @Update("UPDATE users SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
