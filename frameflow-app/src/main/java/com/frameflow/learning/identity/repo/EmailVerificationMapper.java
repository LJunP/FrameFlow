package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmailVerificationMapper {

    @Insert("INSERT INTO email_verification_tokens(user_id, token_hash, expires_at) "
            + "VALUES(#{userId}, #{tokenHash}, #{expiresAt})")
    int insert(@Param("userId") Long userId,
               @Param("tokenHash") String tokenHash,
               @Param("expiresAt") OffsetDateTime expiresAt);

    @Select("SELECT id, user_id, token_hash, expires_at, used_at "
            + "FROM email_verification_tokens WHERE token_hash = #{tokenHash}")
    PasswordResetRow findByTokenHash(String tokenHash);

    @Update("UPDATE email_verification_tokens SET used_at = #{usedAt} "
            + "WHERE user_id = #{userId} AND used_at IS NULL")
    int invalidateOpen(@Param("userId") Long userId, @Param("usedAt") OffsetDateTime usedAt);

    @Update("UPDATE email_verification_tokens SET used_at = #{usedAt} "
            + "WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") OffsetDateTime usedAt);
}
