package com.frameflow.learning.product.repo;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * quality_profiles / quality_profile_versions 表访问。
 * 版本表同样【没有 UPDATE/DELETE】——发布即定格，改标准只能发新版本。
 */
@Mapper
public interface QualityProfileMapper {

    @Select("INSERT INTO quality_profiles(team_id, name, description, created_by) "
            + "VALUES(#{teamId}, #{name}, #{description}, #{createdBy}) RETURNING id")
    Long insertProfile(@Param("teamId") Long teamId,
                       @Param("name") String name,
                       @Param("description") String description,
                       @Param("createdBy") Long createdBy);

    @Select("SELECT id, team_id, name, description, created_by, created_at "
            + "FROM quality_profiles WHERE id = #{id}")
    QualityProfileRow findProfileById(Long id);

    /** latest_version 用相关子查询取（版本表很小，子查询成本可忽略）。 */
    @Select("SELECT p.id, p.team_id, p.name, p.description, p.created_by, p.created_at, "
            + "(SELECT max(version_no) FROM quality_profile_versions v WHERE v.profile_id = p.id) "
            + "AS latest_version "
            + "FROM quality_profiles p WHERE p.team_id = #{teamId} ORDER BY p.id")
    List<QualityProfileRow> listProfilesByTeam(Long teamId);

    // ★ 核心：JSONB 的出入参写法——写入用 #{spec}::jsonb（把字符串参数
    // 显式转成 jsonb，否则 PG 会报"类型不匹配"）；读出用 spec::text
    // （JDBC 原生读 jsonb 得到的是 PGobject，转 text 才是普通字符串）。
    @Insert("INSERT INTO quality_profile_versions"
            + "(profile_id, version_no, spec, published_by) "
            + "VALUES(#{profileId}, #{versionNo}, #{specJson}::jsonb, #{publishedBy})")
    int insertVersion(@Param("profileId") Long profileId,
                      @Param("versionNo") int versionNo,
                      @Param("specJson") String specJson,
                      @Param("publishedBy") Long publishedBy);

    @Select("SELECT max(version_no) FROM quality_profile_versions WHERE profile_id = #{profileId}")
    Integer maxVersionNo(Long profileId);

    @Select("SELECT id, profile_id, version_no, spec::text AS spec_json, published_by, "
            + "published_at FROM quality_profile_versions "
            + "WHERE profile_id = #{profileId} ORDER BY version_no")
    List<QualityProfileVersionRow> listVersions(Long profileId);

    @Select("SELECT id, profile_id, version_no, spec::text AS spec_json, published_by, "
            + "published_at FROM quality_profile_versions "
            + "WHERE profile_id = #{profileId} AND version_no = #{versionNo}")
    QualityProfileVersionRow findVersion(@Param("profileId") Long profileId,
                                         @Param("versionNo") int versionNo);
}
