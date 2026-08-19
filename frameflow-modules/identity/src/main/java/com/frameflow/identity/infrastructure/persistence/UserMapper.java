package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper {

    @Insert("""
            INSERT INTO users (email, password_hash, display_name, status, created_at, updated_at)
            VALUES (#{email}, #{passwordHash}, #{displayName}, #{status}, now(), now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    @Select("SELECT id, email, password_hash AS passwordHash, display_name AS displayName, status, "
            + "last_login_at AS lastLoginAt, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT id, email, password_hash AS passwordHash, display_name AS displayName, status, "
            + "last_login_at AS lastLoginAt, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM users WHERE id = #{id}")
    User findById(@Param("id") long id);

    @Update("UPDATE users SET last_login_at = now(), updated_at = now() WHERE id = #{id}")
    int touchLastLogin(@Param("id") long id);
}
