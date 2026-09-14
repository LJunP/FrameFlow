package com.frameflow.learning.identity.repo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** team_invitations 表访问（邀请生命周期：创建 → 查询 → 接受核销）。 */
@Mapper
public interface InvitationMapper {

    // INSERT ... RETURNING：与 UserMapper.insert 同原理，一次往返拿到新 id。
    // ★ 只写令牌的 SHA-256（V8）——令牌是入团凭证，明文落库等于把凭证存进数据库，
    // 泄露即可直接用。与 refresh_tokens 的做法保持一致。
    @Select("INSERT INTO team_invitations(team_id, email, role, token_hash, expires_at, invited_by) "
            + "VALUES(#{teamId}, #{email}, #{role}, #{tokenHash}, #{expiresAt}, #{invitedBy}) "
            + "RETURNING id")
    Long insert(@Param("teamId") Long teamId,
                @Param("email") String email,
                @Param("role") String role,
                @Param("tokenHash") String tokenHash,
                @Param("expiresAt") OffsetDateTime expiresAt,
                @Param("invitedBy") Long invitedBy);

    /**
     * 接受邀请时按**令牌哈希**查整行；查不到返回 null（调用方据此判"链接无效"）。
     * 查哈希而不是查明文，意味着即使数据库被读走，也凑不出可用的链接。
     */
    @Select("SELECT id, team_id, email, role, token_hash, expires_at, accepted_at, revoked_at, "
            + "created_at, invited_by FROM team_invitations WHERE token_hash = #{tokenHash}")
    InvitationRow findByTokenHash(String tokenHash);

    /**
     * 团队页"邀请记录"列表：待接受的在前，同组按创建时间倒序（最新在上）。
     * ★ 故意不 SELECT token_hash——列表不需要凭证，也就没有理由把它送到网络上。
     * 明文令牌只在"创建邀请"的响应里返回一次，Owner 必须当场保存。
     */
    @Select("SELECT id, team_id, email, role, expires_at, accepted_at, revoked_at, created_at, invited_by "
            + "FROM team_invitations WHERE team_id = #{teamId} "
            + "ORDER BY (accepted_at IS NULL AND revoked_at IS NULL) DESC, created_at DESC")
    List<InvitationRow> listByTeam(Long teamId);

    // ★ 核心：WHERE accepted_at IS NULL 让"核销"天然幂等——两个并发请求同时
    // 接受同一邀请时，只有一个 UPDATE 命中行；配合 team_members 的复合主键，
    // 同一邀请绝不可能把用户重复塞进团队。
    @Update("UPDATE team_invitations SET accepted_at = #{acceptedAt} "
            + "WHERE id = #{id} AND accepted_at IS NULL AND revoked_at IS NULL")
    int markAccepted(@Param("id") Long id, @Param("acceptedAt") OffsetDateTime acceptedAt);

    /** 只作废尚未接受、尚未撤销的邀请；已入团的记录不能靠撤销撤回成员关系。 */
    @Update("UPDATE team_invitations SET revoked_at = #{revokedAt} "
            + "WHERE id = #{id} AND team_id = #{teamId} "
            + "AND accepted_at IS NULL AND revoked_at IS NULL")
    int markRevoked(@Param("id") Long id,
                    @Param("teamId") Long teamId,
                    @Param("revokedAt") OffsetDateTime revokedAt);
}
