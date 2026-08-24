package com.frameflow.learning.product.service;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final Set<String> ROOT_FIELDS =
            Set.of("dimensions", "semantic", "weights", "duplicates");
    private static final Set<String> DIMENSION_FIELDS =
            Set.of("duration", "resolution", "fps");
    private static final Set<String> RULE_SEVERITIES =
            Set.of("BLOCKER", "WARNING", "INFO");
    private static final Set<String> SEMANTIC_DIMENSIONS =
            Set.of("prompt_alignment", "quality_impression", "policy_violation");

    private final QualityProfileMapper profiles;
    private final TeamAccessService teamAccess;
    private final ObjectMapper objectMapper;
    private final SemanticModelCatalogService semanticModels;

    public QualityProfileService(QualityProfileMapper profiles, TeamAccessService teamAccess,
                                 ObjectMapper objectMapper,
                                 SemanticModelCatalogService semanticModels) {
        this.profiles = profiles;
        this.teamAccess = teamAccess;
        this.objectMapper = objectMapper;
        this.semanticModels = semanticModels;
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

    /** spec 入库边界：只接受 Java、Worker 与排名服务都能安全消费的形状。 */
    private String validateSpec(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) {
                throw invalid("$", "顶层必须是对象");
            }
            // ★ 核心：Profile 版本发布后不可修改，所以必须在这个唯一入库口
            // 做完整 schema 防火墙；否则形状错误会被永久冻结并在 Worker/排名时才爆炸。
            requireOnlyFields(node, "$", ROOT_FIELDS);
            validateDimensions(node.get("dimensions"));
            validateSemantic(node.get("semantic"));
            validateWeights(node.get("weights"));
            validateDuplicates(node.get("duplicates"));
            return node.toString();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_SPEC);
        }
    }

    private void validateDimensions(JsonNode dimensions) {
        if (dimensions == null) {
            return;
        }
        requireObject(dimensions, "$.dimensions");
        requireOnlyFields(dimensions, "$.dimensions", DIMENSION_FIELDS);
        if (dimensions.has("duration")) {
            validateDuration(dimensions.get("duration"));
        }
        if (dimensions.has("resolution")) {
            validateResolution(dimensions.get("resolution"));
        }
        if (dimensions.has("fps")) {
            validateFps(dimensions.get("fps"));
        }
    }

    private void validateDuration(JsonNode duration) {
        requireObject(duration, "$.dimensions.duration");
        requireOnlyFields(duration, "$.dimensions.duration", Set.of("min", "max", "severity"));
        if (!duration.has("min") && !duration.has("max")) {
            throw invalid("$.dimensions.duration", "min/max 至少需要一项");
        }
        Double minimum = duration.has("min")
                ? requireFiniteNumber(duration.get("min"), "$.dimensions.duration.min") : null;
        Double maximum = duration.has("max")
                ? requireFiniteNumber(duration.get("max"), "$.dimensions.duration.max") : null;
        if (minimum != null && minimum < 0) {
            throw invalid("$.dimensions.duration.min", "不能小于 0");
        }
        if (maximum != null && maximum < 0) {
            throw invalid("$.dimensions.duration.max", "不能小于 0");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw invalid("$.dimensions.duration", "min 不能大于 max");
        }
        validateSeverity(duration, "$.dimensions.duration");
    }

    private void validateResolution(JsonNode resolution) {
        requireObject(resolution, "$.dimensions.resolution");
        requireOnlyFields(resolution, "$.dimensions.resolution",
                Set.of("minWidth", "minHeight", "severity"));
        if (!resolution.has("minWidth") || !resolution.has("minHeight")) {
            throw invalid("$.dimensions.resolution", "minWidth/minHeight 必须成对提供");
        }
        int width = requirePositiveInt(resolution.get("minWidth"),
                "$.dimensions.resolution.minWidth");
        int height = requirePositiveInt(resolution.get("minHeight"),
                "$.dimensions.resolution.minHeight");
        if (width < 1 || height < 1) {
            throw invalid("$.dimensions.resolution", "宽高必须是正整数");
        }
        validateSeverity(resolution, "$.dimensions.resolution");
    }

    private void validateFps(JsonNode fps) {
        requireObject(fps, "$.dimensions.fps");
        requireOnlyFields(fps, "$.dimensions.fps", Set.of("min", "severity"));
        if (!fps.has("min")) {
            throw invalid("$.dimensions.fps.min", "必须提供");
        }
        double minimum = requireFiniteNumber(fps.get("min"), "$.dimensions.fps.min");
        if (minimum < 1 || minimum > 240) {
            throw invalid("$.dimensions.fps.min", "必须在 1..240 之间");
        }
        validateSeverity(fps, "$.dimensions.fps");
    }

    private void validateSemantic(JsonNode semantic) {
        if (semantic == null) {
            return;
        }
        requireObject(semantic, "$.semantic");
        requireOnlyFields(semantic, "$.semantic", Set.of("enabled", "dimensions", "modelId"));
        JsonNode enabled = semantic.get("enabled");
        if (enabled == null || !enabled.isBoolean()) {
            throw invalid("$.semantic.enabled", "必须是布尔值");
        }

        JsonNode modelId = semantic.get("modelId");
        if (modelId != null) {
            if (!modelId.isTextual() || modelId.textValue().isBlank()) {
                throw invalid("$.semantic.modelId", "必须是非空字符串");
            }
            if (!semanticModels.isEnabled(modelId.textValue())) {
                throw invalid("$.semantic.modelId", "模型不存在或已停用");
            }
        } else if (enabled.booleanValue()) {
            // ★ 核心：新发布的 Profile 是不可变快照。调用方省略 modelId 时，
            // 在入库前固化当时的默认模型；否则平台以后切换默认值，会让同一个
            // 历史 Profile 悄悄改用另一供应商，证据无法复现。旧数据库行不经过
            // 本入口，仍保留缺省形状交由 Worker 的 legacy default 兼容。
            ((ObjectNode) semantic).put("modelId", semanticModels.defaultModelId());
        }
        if (!semantic.has("dimensions")) {
            return; // 兼容已发布 spec：缺省时 Worker 使用三个默认语义维度
        }
        JsonNode dimensions = semantic.get("dimensions");
        if (!dimensions.isArray()) {
            throw invalid("$.semantic.dimensions", "必须是数组");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < dimensions.size(); i++) {
            JsonNode dimension = dimensions.get(i);
            if (!dimension.isTextual() || !SEMANTIC_DIMENSIONS.contains(dimension.textValue())) {
                throw invalid("$.semantic.dimensions[" + i + "]", "不在允许白名单中");
            }
            if (!seen.add(dimension.textValue())) {
                throw invalid("$.semantic.dimensions[" + i + "]", "不得重复");
            }
        }
    }

    private void validateWeights(JsonNode weights) {
        if (weights == null) {
            return;
        }
        requireObject(weights, "$.weights");
        weights.fields().forEachRemaining(entry -> {
            String path = "$.weights." + entry.getKey();
            JsonNode weight = entry.getValue();
            if (!weight.isIntegralNumber() || !weight.canConvertToInt()) {
                throw invalid(path, "必须是整数");
            }
            int value = weight.intValue();
            if (value < 0 || value > 100) {
                throw invalid(path, "必须在 0..100 之间");
            }
        });
    }

    private void validateDuplicates(JsonNode duplicates) {
        if (duplicates == null) {
            return;
        }
        requireObject(duplicates, "$.duplicates");
        requireOnlyFields(duplicates, "$.duplicates", Set.of("hammingThreshold"));
        if (!duplicates.has("hammingThreshold")) {
            throw invalid("$.duplicates.hammingThreshold", "必须提供");
        }
        JsonNode threshold = duplicates.get("hammingThreshold");
        if (!threshold.isIntegralNumber() || !threshold.canConvertToInt()) {
            throw invalid("$.duplicates.hammingThreshold", "必须是整数");
        }
        int value = threshold.intValue();
        if (value < 0 || value > 64) {
            throw invalid("$.duplicates.hammingThreshold", "必须在 0..64 之间");
        }
    }

    private void validateSeverity(JsonNode rule, String path) {
        if (!rule.has("severity")) {
            return;
        }
        JsonNode severity = rule.get("severity");
        if (!severity.isTextual() || !RULE_SEVERITIES.contains(severity.textValue())) {
            throw invalid(path + ".severity", "必须是 BLOCKER/WARNING/INFO 之一");
        }
    }

    private void requireObject(JsonNode node, String path) {
        if (!node.isObject()) {
            throw invalid(path, "必须是对象");
        }
    }

    private void requireOnlyFields(JsonNode node, String path, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalid(path + "." + field, "未知字段");
            }
        });
    }

    private double requireFiniteNumber(JsonNode node, String path) {
        if (!node.isNumber()) {
            throw invalid(path, "必须是数字");
        }
        double value = node.doubleValue();
        if (!Double.isFinite(value)) {
            throw invalid(path, "必须是有限数");
        }
        return value;
    }

    private int requirePositiveInt(JsonNode node, String path) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(path, "必须是整数");
        }
        return node.intValue();
    }

    private ApiException invalid(String path, String reason) {
        return new ApiException(ErrorCode.INVALID_SPEC, "质检标准不合法: " + path + " " + reason);
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
