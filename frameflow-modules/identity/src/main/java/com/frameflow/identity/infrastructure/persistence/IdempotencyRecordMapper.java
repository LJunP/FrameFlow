package com.frameflow.identity.infrastructure.persistence;

import com.frameflow.identity.domain.IdempotencyRecord;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface IdempotencyRecordMapper {

    @Insert("INSERT INTO idempotency_records "
            + "(scope, idempotency_key, request_hash, status, expires_at, created_at, updated_at) "
            + "VALUES (#{scope}, #{idempotencyKey}, #{requestHash}, 'PROCESSING', #{expiresAt}, now(), now()) "
            + "ON CONFLICT (scope, idempotency_key) DO NOTHING")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIgnore(IdempotencyRecord record);

    @Select("SELECT id, scope, idempotency_key AS idempotencyKey, request_hash AS requestHash, status, "
            + "response_status AS responseStatus, response_body::text AS responseBody, "
            + "expires_at AS expiresAt, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM idempotency_records WHERE scope = #{scope} AND idempotency_key = #{key} FOR UPDATE")
    IdempotencyRecord findByScopeAndKeyForUpdate(@Param("scope") String scope, @Param("key") UUID key);

    @Select("SELECT id, scope, idempotency_key AS idempotencyKey, request_hash AS requestHash, status, "
            + "response_status AS responseStatus, response_body::text AS responseBody, "
            + "expires_at AS expiresAt, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM idempotency_records WHERE scope = #{scope} AND idempotency_key = #{key,typeHandler=com.frameflow.identity.infrastructure.persistence.UuidTypeHandler}")
    IdempotencyRecord findByScopeAndKey(@Param("scope") String scope, @Param("key") UUID key);

    @Update("UPDATE idempotency_records SET status = 'COMPLETED', response_status = #{status}, "
            + "response_body = #{body}::jsonb, updated_at = now() WHERE id = #{id}")
    int complete(@Param("id") long id, @Param("status") int status, @Param("body") String body);

    @Delete("DELETE FROM idempotency_records WHERE scope = #{scope} AND idempotency_key = #{key}")
    int deleteByScopeAndKey(@Param("scope") String scope, @Param("key") UUID key);
}
