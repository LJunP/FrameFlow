package com.frameflow.learning.product.web;

import java.util.List;

import com.frameflow.learning.product.service.BatchService;
import com.frameflow.learning.product.service.ProjectService;
import com.frameflow.learning.product.web.BatchDtos.BatchResponse;
import com.frameflow.learning.product.web.ProductDtos.ArchiveProjectRequest;
import com.frameflow.learning.product.web.ProductDtos.BriefResponse;
import com.frameflow.learning.product.web.ProductDtos.CreateProjectRequest;
import com.frameflow.learning.product.web.ProductDtos.ProjectResponse;
import com.frameflow.learning.product.web.ProductDtos.PublishBriefRequest;
import com.frameflow.learning.product.web.ProductDtos.UpdateProjectRequest;
import com.frameflow.learning.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project 与 Brief 接口。
 *
 * 【F2 阅读起点】ProjectService 顶部的权限矩阵注释 → 本类 → ProductDtos。
 * Controller 刻意保持"薄"：只做参数转发，规则全部在 Service——
 * 这是让权限与业务规则可被独立测试的结构保证。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final BatchService batchService;

    public ProjectController(ProjectService projectService, BatchService batchService) {
        this.projectService = projectService;
        this.batchService = batchService;
    }

    // ★ 核心：teamId 取自 JWT 的 tid claim（登录时签发写入），不信任任何
    // 请求参数里的团队归属——"我在哪个团队"只认令牌，杜绝越权参数伪造。
    private static long teamId(Jwt jwt) {
        return jwt.getClaim("tid");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateProjectRequest req) {
        return projectService.create(Long.parseLong(jwt.getSubject()), teamId(jwt), req);
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return projectService.list(Long.parseLong(jwt.getSubject()), teamId(jwt), page, size);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return projectService.get(Long.parseLong(jwt.getSubject()), id);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                  @Valid @RequestBody UpdateProjectRequest req) {
        return projectService.update(Long.parseLong(jwt.getSubject()), id, req);
    }

    @PostMapping("/{id}/archive")
    public ProjectResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                   @Valid @RequestBody ArchiveProjectRequest req) {
        return projectService.archive(Long.parseLong(jwt.getSubject()), id, req);
    }

    // ---------- Brief ----------

    @PostMapping("/{id}/briefs")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse publishBrief(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                      @Valid @RequestBody PublishBriefRequest req) {
        return projectService.publishBrief(Long.parseLong(jwt.getSubject()), id, req);
    }

    @GetMapping("/{id}/briefs")
    public List<BriefResponse> listBriefs(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return projectService.listBriefs(Long.parseLong(jwt.getSubject()), id);
    }

    @GetMapping("/{id}/briefs/current")
    public BriefResponse currentBrief(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return projectService.currentBrief(Long.parseLong(jwt.getSubject()), id);
    }

    // ---------- Batches ----------

    /** 历史批次列表：成员可读，供项目页回看已创建的批次（新批次在前）。 */
    @GetMapping("/{id}/batches")
    public List<BatchResponse> listBatches(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return batchService.listByProject(Long.parseLong(jwt.getSubject()), id);
    }
}
