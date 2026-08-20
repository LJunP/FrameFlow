package com.frameflow.product.web;

import com.frameflow.product.api.ApiException;
import com.frameflow.product.api.TeamGuard;
import com.frameflow.product.application.ProjectService;
import com.frameflow.product.domain.Project;
import com.frameflow.product.domain.QualityProfile;
import com.frameflow.product.domain.QualityProfileVersion;
import com.frameflow.product.web.dto.ProductDtos.CreateProfileRequest;
import com.frameflow.product.web.dto.ProductDtos.CreateProfileVersionRequest;
import com.frameflow.product.web.dto.ProductDtos.CreateProjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "product", description = "Project 与 Quality Profile")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService service;
    private final TeamGuard guard;

    public ProjectController(ProjectService service, TeamGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    private long userId(Authentication auth) {
        Object p = auth == null ? null : auth.getPrincipal();
        if (p instanceof com.frameflow.identity.api.IdentityPrincipal cu) {
            return cu.userId();
        }
        throw ApiException.forbidden("unauthenticated");
    }

    @PostMapping("/projects")
    @Operation(operationId = "createProject", summary = "创建项目（OWNER/OPERATOR）")
    public ResponseEntity<Map<String, Long>> createProject(Authentication auth,
            @RequestHeader("X-Team-Id") long teamId,
            @Valid @RequestBody CreateProjectRequest req) {
        long uid = userId(auth);
        guard.requireOwnerOrOperator(uid, teamId);
        long id = service.createProject(new ProjectService.CreateProjectCmd(teamId, uid, req.name(), req.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/projects")
    @Operation(operationId = "listProjects", summary = "团队项目列表")
    public ResponseEntity<List<Project>> listProjects(Authentication auth,
            @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        guard.requireMember(uid, teamId);
        return ResponseEntity.ok(service.listProjects(teamId));
    }

    @GetMapping("/projects/{projectId}")
    @Operation(operationId = "getProject", summary = "获取项目")
    public ResponseEntity<Project> getProject(Authentication auth, @PathVariable long projectId) {
        long uid = userId(auth);
        Project p = service.getProjectFromAnyTeam(projectId);
        guard.requireMember(uid, p.getTeamId());
        return ResponseEntity.ok(p);
    }

    @PostMapping("/projects/{projectId}/quality-profiles")
    @Operation(operationId = "createQualityProfile", summary = "创建质量模板草稿（OWNER/OPERATOR）")
    public ResponseEntity<Map<String, Long>> createQualityProfile(Authentication auth,
            @PathVariable long projectId, @Valid @RequestBody CreateProfileRequest req) {
        long uid = userId(auth);
        Project p = service.getProjectFromAnyTeam(projectId);
        guard.requireOwnerOrOperator(uid, p.getTeamId());
        long id = service.createProfile(new ProjectService.CreateProfileCmd(p.getTeamId(), projectId, uid,
                req.name(), req.templateType() == null ? "ECOMMERCE_SHORT_AD_V1" : req.templateType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/projects/{projectId}/quality-profiles")
    @Operation(operationId = "listQualityProfiles", summary = "项目质量模板列表")
    public ResponseEntity<List<QualityProfile>> listQualityProfiles(Authentication auth,
            @PathVariable long projectId) {
        long uid = userId(auth);
        Project p = service.getProjectFromAnyTeam(projectId);
        guard.requireMember(uid, p.getTeamId());
        return ResponseEntity.ok(service.listProfiles(p.getTeamId(), projectId));
    }

    @PostMapping("/projects/{projectId}/quality-profiles/{profileId}/versions")
    @Operation(operationId = "createProfileVersion", summary = "保存质量模板版本草稿")
    public ResponseEntity<QualityProfileVersion> createProfileVersion(Authentication auth,
            @PathVariable long projectId, @PathVariable long profileId,
            @Valid @RequestBody CreateProfileVersionRequest req) {
        long uid = userId(auth);
        Project p = service.getProjectFromAnyTeam(projectId);
        guard.requireOwnerOrOperator(uid, p.getTeamId());
        QualityProfileVersion v = service.createDraftVersion(p.getTeamId(), profileId, uid, req.payload());
        return ResponseEntity.status(HttpStatus.CREATED).body(v);
    }

    @PostMapping("/quality-profile-versions/{versionId}/publish")
    @Operation(operationId = "publishProfileVersion", summary = "发布模板版本（不可变，仅 OWNER）")
    public ResponseEntity<QualityProfileVersion> publishProfileVersion(Authentication auth,
            @PathVariable long versionId) {
        long uid = userId(auth);
        ProjectService.VersionStorage storage = service.profileStorage(versionId);
        guard.requireOwner(uid, storage.teamId());
        QualityProfileVersion v = service.publishVersion(storage.teamId(), storage.profileId(), uid, storage.version());
        return ResponseEntity.ok(v);
    }
}