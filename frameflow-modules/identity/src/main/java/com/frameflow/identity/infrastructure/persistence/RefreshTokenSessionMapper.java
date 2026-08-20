package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.domain.RefreshTokenSession;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RefreshTokenSessionMapper {

    @Insert("INSERT INTO refresh_token_sessions "
            + "(user_id, family_id, token_hash, status, expires_at, created_at, updated_at) "
            + "VALUES (#{userId}, #{familyId}, #{tokenHash}, #{status}, #{expiresAt}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(RefreshTokenSession session);

    @Select("SELECT id, user_id AS userId, family_id AS familyId, token_hash AS tokenHash, status, "
            + "expires_at AS expiresAt, last_used_at AS lastUsedAt, rotated_at AS rotatedAt, "
            + "revoked_at AS revokedAt, replaced_by_id AS replacedById, "
            + "created_at AS createdAt, updated_at AS updatedAt "
            + "FROM refresh_token_sessions WHERE token_hash = #{hash} FOR UPDATE")
    RefreshTokenSession findByHashForUpdate(@Param("hash") String hash);

    @Update("UPDATE refresh_token_sessions SET status = 'REVOKED', revoked_at = now(), updated_at = now() "
            + "WHERE family_id = #{familyId,typeHandler=com.frameflow.identity.infrastructure.persistence.UuidTypeHandler} AND status = 'ACTIVE'")
    int revokeActiveByFamily(@Param("familyId") UUID familyId);

    @Update("UPDATE refresh_token_sessions SET status = 'ROTATED', rotated_at = now(), "
            + "replaced_by_id = #{replacedBy}, last_used_at = now(), updated_at = now() "
            + "WHERE id = #{id}")
    int markRotated(@Param("id") long id, @Param("replacedBy") long replacedBy);

    @Update("UPDATE refresh_token_sessions SET last_used_at = now(), updated_at = now() WHERE id = #{id}")
    int touchLastUsed(@Param("id") long id);
}
