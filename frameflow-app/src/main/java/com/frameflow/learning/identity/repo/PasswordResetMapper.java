package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 找回密码令牌：只存 SHA-256，核销用条件更新保证一次性。 */
@Mapper
public interface PasswordResetMapper {

    @Insert("INSERT INTO password_reset_tokens(user_id, token_hash, expires_at) "
            + "VALUES(#{userId}, #{tokenHash}, #{expiresAt})")
    int insert(@Param("userId") Long userId,
               @Param("tokenHash") String tokenHash,
               @Param("expiresAt") OffsetDateTime expiresAt);

    @Select("SELECT id, user_id, token_hash, expires_at, used_at, created_at "
            + "FROM password_reset_tokens WHERE token_hash = #{tokenHash}")
    PasswordResetRow findByTokenHash(String tokenHash);

    /** 同一用户新申请时作废未使用的旧令牌，邮箱里/日志里的旧链接立即失效。 */
    @Update("UPDATE password_reset_tokens SET used_at = #{usedAt} "
            + "WHERE user_id = #{userId} AND used_at IS NULL")
    int invalidateOpen(@Param("userId") Long userId, @Param("usedAt") OffsetDateTime usedAt);

    @Update("UPDATE password_reset_tokens SET used_at = #{usedAt} "
            + "WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") OffsetDateTime usedAt);
}
