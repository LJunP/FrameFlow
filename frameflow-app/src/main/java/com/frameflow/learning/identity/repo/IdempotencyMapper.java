package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * idempotency_records 表访问（T5 幂等中间件的存储层）。
 */
@Mapper
public interface IdempotencyMapper {

    // ★ 核心：ON CONFLICT DO NOTHING 是并发安全的幂等写入——
    // 两个相同 (scope, key) 的请求同时到达，受复合主键保护，只有一个 INSERT
    // 真正生效（返回 1），另一个返回 0 并走"读已存响应"分支。
    // 如果不用这个语法而先 SELECT 再 INSERT，两个请求可能都查到"不存在"
    // 然后各自插入，幂等被并发击穿。
    @Insert("INSERT INTO idempotency_records"
            + "(scope, idempotency_key, response_status, response_body, expires_at) "
            + "VALUES(#{scope}, #{idempotencyKey}, #{responseStatus}, #{responseBody}, #{expiresAt}) "
            + "ON CONFLICT DO NOTHING")
    int insertIfAbsent(@Param("scope") String scope,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("responseStatus") int responseStatus,
                       @Param("responseBody") String responseBody,
                       @Param("expiresAt") OffsetDateTime expiresAt);

    /** 只取未过期的记录；过期视为"没有幂等记录"，重新执行业务。 */
    @Select("SELECT response_status, response_body FROM idempotency_records "
            + "WHERE scope = #{scope} AND idempotency_key = #{idempotencyKey} "
            + "AND expires_at > #{now}")
    IdempotencyRecordRow findValid(@Param("scope") String scope,
                                   @Param("idempotencyKey") String idempotencyKey,
                                   @Param("now") OffsetDateTime now);
}
