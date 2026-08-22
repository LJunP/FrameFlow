package com.frameflow.learning.product.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.service.TeamAccessService;
import com.frameflow.learning.product.repo.QualityProfileMapper;
import com.frameflow.learning.product.repo.QualityProfileRow;
import com.frameflow.learning.product.repo.QualityProfileVersionRow;
import com.frameflow.learning.product.web.ProductDtos.CreateProfileRequest;
import com.frameflow.learning.product.web.ProductDtos.ProfileResponse;
import com.frameflow.learning.product.web.ProductDtos.ProfileVersionResponse;
import com.frameflow.learning.product.web.ProductDtos.PublishVersionRequest;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quality Profile 的业务逻辑：团队级质检标准，创建即发布 v1，
 * 之后只能追加新版本，任何版本发布后不可修改。
 */
@Service
public class QualityProfileService {

    private final QualityProfileMapper profiles;
    private final TeamAccessService teamAccess;
    private final ObjectMapper objectMapper;

    public QualityProfileService(QualityProfileMapper profiles, TeamAccessService teamAccess,
                                 ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.teamAccess = teamAccess;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProfileResponse create(long userId, long teamId, CreateProfileRequest req) {
        teamAccess.requireRole(userId, teamId, Role.OWNER, Role.OPERATOR);
        String specJson = validateSpec(req.spec());
        try {
            Long profileId = profiles.insertProfile(teamId, req.name(), req.description(), userId);
            // 创建即发布 v1：不存在"没有版本的 profile"，简化引用方逻辑
            profiles.insertVersion(profileId, 1, specJson, userId);
            return toResponse(profiles.findProfileById(profileId), 1);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.NAME_ALREADY_EXISTS);
        }
    }

    public List<ProfileResponse> list(long userId, long teamId) {
        teamAccess.requireMember(userId, teamId);
        return profiles.listProfilesByTeam(teamId).stream()
                .map(row -> toResponse(row, row.getLatestVersion())).toList();
    }

    public List<ProfileVersionResponse> listVersions(long userId, long profileId) {
        QualityProfileRow profile = requireProfileOfMyTeam(userId, profileId);
        return profiles.listVersions(profile.getId()).stream().map(this::toVersionResponse).toList();
    }

    public ProfileVersionResponse getVersion(long userId, long profileId, int versionNo) {
        QualityProfileRow profile = requireProfileOfMyTeam(userId, profileId);
        QualityProfileVersionRow version = profiles.findVersion(profile.getId(), versionNo);
        if (version == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toVersionResponse(version);
    }

    @Transactional
    public ProfileVersionResponse publishVersion(long userId, long profileId, PublishVersionRequest req) {
        QualityProfileRow profile = requireProfileOfMyTeam(userId, profileId);
        teamAccess.requireRole(userId, profile.getTeamId(), Role.OWNER, Role.OPERATOR);
        String specJson = validateSpec(req.spec());

        // ★ 核心：并发安全的版本号生成——"算下一个版本号"和"插入"之间存在
        // 缝隙：两个发布请求都算出 next=3 时，靠 (profile_id, version_no)
        // UNIQUE 约束让后插入者失败；捕获后重算一次即可（此时 max 已是 3，
        // 算出 4）。最多重试一次，再冲突说明竞争极端，宁可 500 也不锁表。
        // 若没有这个约束兜底，"先查 max 再插入"在并发下会产生重复版本号。
        for (int attempt = 0; attempt < 2; attempt++) {
            Integer max = profiles.maxVersionNo(profile.getId());
            int next = (max == null) ? 1 : max + 1;
            try {
                profiles.insertVersion(profile.getId(), next, specJson, userId);
                return toVersionResponse(profiles.findVersion(profile.getId(), next));
            } catch (DuplicateKeyException e) {
                // 版本号被并发占用，重算（第二次仍冲突则抛出转 500）
            }
        }
        throw new IllegalStateException("版本号并发冲突重试仍失败, profileId=" + profileId);
    }

    // ---------- 内部 ----------

    /**
     * spec 校验：必须是合法 JSON 且顶层是对象。
     * F2 不锁死内部结构（维度与权重 schema 随 F4/F6 演进），
     * 但"入库前必须可解析"从第一天就守住——坏 JSON 一旦进了 JSONB
     * 列，下游所有消费者都会炸。
     */
    private String validateSpec(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw new ApiException(ErrorCode.INVALID_SPEC);
            }
            return node.toString();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_SPEC);
        }
    }

    private QualityProfileRow requireProfileOfMyTeam(long userId, long profileId) {
        QualityProfileRow profile = profiles.findProfileById(profileId);
        if (profile == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        teamAccess.requireMember(userId, profile.getTeamId());
        return profile;
    }

    private ProfileResponse toResponse(QualityProfileRow row, Integer latestVersion) {
        return new ProfileResponse(row.getId(), row.getName(), row.getDescription(), latestVersion);
    }

    private ProfileVersionResponse toVersionResponse(QualityProfileVersionRow row) {
        JsonNode spec;
        try {
            spec = objectMapper.readTree(row.getSpecJson());
        } catch (Exception e) {
            // 理论不可达：入库前已校验；若真发生说明数据被外部污染
            throw new IllegalStateException("存量 spec 无法解析, versionId=" + row.getId(), e);
        }
        return new ProfileVersionResponse(row.getId(), row.getProfileId(), row.getVersionNo(),
                spec, row.getPublishedBy(), row.getPublishedAt().toString());
    }
}
