package com.frameflow.product.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.product.api.ApiException;
import com.frameflow.product.domain.Project;
import com.frameflow.product.domain.QualityProfile;
import com.frameflow.product.domain.QualityProfileVersion;
import com.frameflow.product.infrastructure.persistence.ProjectRepositories;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepositories repos;
    private final ObjectMapper mapper;

    public ProjectService(ProjectRepositories repos, ObjectMapper mapper) {
        this.repos = repos;
        this.mapper = mapper;
    }

    public record CreateProjectCmd(long teamId, long userId, String name, String description) {
    }

    public record CreateProfileCmd(long teamId, long projectId, long userId, String name,
                                   String templateType) {
    }

    @Transactional
    public long createProject(CreateProjectCmd cmd) {
        return repos.create(cmd.teamId(), cmd.userId(), cmd.name(), cmd.description());
    }

    public List<Project> listProjects(long teamId) {
        return repos.listByTeam(teamId);
    }

    public Project getProject(long teamId, long id) {
        return repos.require(teamId, id);
    }

    public Project getProjectFromAnyTeam(long id) {
        return repos.findByIdAnyTeam(id);
    }

    public record VersionStorage(long teamId, long profileId, int version) {
    }

    public VersionStorage profileStorage(long versionId) {
        Long teamId = repos.teamOfVersion(versionId);
        if (teamId == null) {
            throw ApiException.notFound("profile version not found");
        }
        long profileId = repos.profileIdOfVersion(versionId);
        int version = repos.versionOf(versionId);
        return new VersionStorage(teamId, profileId, version);
    }

    @Transactional
    public long createProfile(CreateProfileCmd cmd) {
        repos.require(cmd.teamId(), cmd.projectId());
        return repos.createProfile(cmd.teamId(), cmd.projectId(), cmd.userId(), cmd.name(), cmd.templateType());
    }

    public List<QualityProfile> listProfiles(long teamId, long projectId) {
        return repos.listProfiles(teamId, projectId);
    }

    public QualityProfile getProfile(long teamId, long id) {
        return repos.requireProfile(teamId, id);
    }

    @Transactional
    public QualityProfileVersion createDraftVersion(long teamId, long profileId, long userId, String payloadJson) {
        QualityProfile profile = repos.requireProfile(teamId, profileId);
        JsonNode payload;
        try {
            payload = mapper.readTree(payloadJson);
        } catch (Exception ex) {
            throw ApiException.badRequest("profile payload is not valid JSON");
        }
        validateTemplatePayload(payload, profile.getTemplateType());
        int version = repos.nextVersion(profileId);
        String canonical = canonicalJson(payload);
        String digest = sha256(canonical);
        long id = repos.insertVersion(profileId, version, "DRAFT", canonical, digest, userId);
        return repos.requireVersion(profileId, version);
    }

    @Transactional
    public QualityProfileVersion publishVersion(long teamId, long profileId, long userId, int version) {
        repos.requireProfile(teamId, profileId);
        QualityProfileVersion v = findVersionOrThrow(profileId, version);
        if ("PUBLISHED".equals(v.getStatus())) {
            throw ApiException.conflict("PROFILE_ALREADY_PUBLISHED", "quality profile version already published");
        }
        repos.updateVersionStatus(v.getId(), "PUBLISHED");
        // A published version is immutable: only status changes to PUBLISHED are allowed.
        return repos.requireVersion(profileId, version);
    }

    public QualityProfileVersion getVersion(long teamId, long profileId, int version) {
        repos.requireProfile(teamId, profileId);
        return findVersionOrThrow(profileId, version);
    }

    private QualityProfileVersion findVersionOrThrow(long profileId, int version) {
        return repos.requireVersion(profileId, version);
    }

    private void validateTemplatePayload(JsonNode payload, String templateType) {
        if (!"ECOMMERCE_SHORT_AD_V1".equals(templateType)) {
            return;
        }
        JsonNode rules = payload.get("rules");
        if (rules != null && !rules.isArray()) {
            throw ApiException.badRequest("rules must be an array");
        }
    }

    static String canonicalJson(JsonNode node) {
        try {
            return new ObjectMapper().writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
