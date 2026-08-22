package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** refresh_tokens 表访问（刷新令牌的轮换与吊销）。 */
@Mapper
public interface RefreshTokenMapper {

    @Insert("INSERT INTO refresh_tokens(user_id, token_hash, expires_at) "
            + "VALUES(#{userId}, #{tokenHash}, #{expiresAt})")
    int insert(@Param("userId") Long userId,
               @Param("tokenHash") String tokenHash,
               @Param("expiresAt") OffsetDateTime expiresAt);

    @Select("SELECT id, user_id, token_hash, expires_at, revoked_at "
            + "FROM refresh_tokens WHERE token_hash = #{tokenHash}")
    RefreshTokenRow findByTokenHash(String tokenHash);

    @Update("UPDATE refresh_tokens SET revoked_at = #{revokedAt} WHERE id = #{id}")
    int markRevoked(@Param("id") Long id, @Param("revokedAt") OffsetDateTime revokedAt);

    /** 登出用：吊销该用户全部刷新令牌。 */
    @Update("UPDATE refresh_tokens SET revoked_at = #{revokedAt} "
            + "WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
