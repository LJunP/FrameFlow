package com.frameflow.learning.product.service;

import java.util.List;

import com.frameflow.learning.identity.domain.Role;
import com.frameflow.learning.identity.service.TeamAccessService;
import com.frameflow.learning.product.repo.BriefMapper;
import com.frameflow.learning.product.repo.BriefRow;
import com.frameflow.learning.product.repo.ProjectMapper;
import com.frameflow.learning.product.repo.ProjectRow;
import com.frameflow.learning.product.web.ProductDtos.ArchiveProjectRequest;
import com.frameflow.learning.product.web.ProductDtos.BriefResponse;
import com.frameflow.learning.product.web.ProductDtos.CreateProjectRequest;
import com.frameflow.learning.product.web.ProductDtos.ProjectResponse;
import com.frameflow.learning.product.web.ProductDtos.PublishBriefRequest;
import com.frameflow.learning.product.web.ProductDtos.UpdateProjectRequest;
import com.frameflow.learning.shared.api.PageResponse;
import com.frameflow.learning.shared.error.ApiException;
import com.frameflow.learning.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project 与 Brief 的业务逻辑。
 *
 * F2 权限矩阵（docs/01 §4 的落地）：
 * - 读（项目/Brief/Profile）：全部成员（OWNER/OPERATOR/REVIEWER/VIEWER）
 * - 建项目 / 发 Brief / 建与发版 Profile / 改项目：OWNER 或 OPERATOR
 * - 归档项目：仅 OWNER（影响面大的操作收得更紧）
 */
@Service
public class ProjectService {

    private final ProjectMapper projects;
    private final BriefMapper briefs;
    private final TeamAccessService teamAccess;

    public ProjectService(ProjectMapper projects, BriefMapper briefs, TeamAccessService teamAccess) {
        this.projects = projects;
        this.briefs = briefs;
        this.teamAccess = teamAccess;
    }

    // ---------- Project ----------

    @Transactional
    public ProjectResponse create(long userId, long teamId, CreateProjectRequest req) {
        teamAccess.requireRole(userId, teamId, Role.OWNER, Role.OPERATOR);
        try {
            Long id = projects.insert(teamId, req.name(), req.description(), userId);
            return toResponse(projects.findById(id));
        } catch (DuplicateKeyException e) {
            // (team_id, name) UNIQUE 兜底，语义与 F1 邮箱重复一致
            throw new ApiException(ErrorCode.NAME_ALREADY_EXISTS);
        }
    }

    public PageResponse<ProjectResponse> list(long userId, long teamId, int page, int size) {
        teamAccess.requireMember(userId, teamId);
        // 参数清洗在服务层做：不信任任何来自外部的分页参数
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ProjectResponse> items = projects
                .findByTeamPage(teamId, safeSize, safePage * safeSize)
                .stream().map(this::toResponse).toList();
        return PageResponse.of(items, safePage, safeSize, projects.countByTeam(teamId));
    }

    public ProjectResponse get(long userId, long projectId) {
        return toResponse(requireProjectOfMyTeam(userId, projectId));
    }

    @Transactional
    public ProjectResponse update(long userId, long projectId, UpdateProjectRequest req) {
        ProjectRow project = requireProjectOfMyTeam(userId, projectId);
        teamAccess.requireRole(userId, project.getTeamId(), Role.OWNER, Role.OPERATOR);
        // ★ 核心：乐观锁冲突判定——UPDATE 带 WHERE lock_version=期望值，
        // 0 行说明"从你读走到你提交"之间有人改过（或已归档），拒绝写入。
        // 客户端唯一正确的恢复方式：重新 GET 拿最新版再改（人肉合并）。
        // 若不做这一步，后提交者会无声覆盖前者的修改（丢失更新问题）。
        int rows = projects.update(projectId, req.name(), req.description(), req.lockVersion());
        if (rows == 0) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT);
        }
        return toResponse(projects.findById(projectId));
    }

    @Transactional
    public ProjectResponse archive(long userId, long projectId, ArchiveProjectRequest req) {
        ProjectRow project = requireProjectOfMyTeam(userId, projectId);
        // 归档只许 OWNER：OPERATOR 能日常管理但无权"关停"项目
        teamAccess.requireRole(userId, project.getTeamId(), Role.OWNER);
        int rows = projects.archive(projectId, req.lockVersion());
        if (rows == 0) {
            // 已非 ACTIVE（重复归档）与版本过期分开报，客户端行为不同
            if ("ARCHIVED".equals(project.getStatus())) {
                throw new ApiException(ErrorCode.PROJECT_ARCHIVED);
            }
            throw new ApiException(ErrorCode.VERSION_CONFLICT);
        }
        return toResponse(projects.findById(projectId));
    }

    // ---------- Brief（不可变快照） ----------

    @Transactional
    public BriefResponse publishBrief(long userId, long projectId, PublishBriefRequest req) {
        ProjectRow project = requireProjectOfMyTeam(userId, projectId);
        teamAccess.requireRole(userId, project.getTeamId(), Role.OWNER, Role.OPERATOR);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED);
        }
        // ★ 核心：不可变快照的写入姿势——只 INSERT 新行，然后把项目的
        // current_brief_id 指针前移。两步在同一个事务里：要么新快照生效，
        // 要么什么都没发生。历史快照行永远原地不动，任何旧批次
        // （F3 起）引用它都能拿到创建当时的原文。
        Long briefId = briefs.insert(projectId, req.content(), userId);
        projects.pointCurrentBriefTo(projectId, briefId);
        return toBriefResponse(briefs.findById(briefId));
    }

    public List<BriefResponse> listBriefs(long userId, long projectId) {
        requireProjectOfMyTeam(userId, projectId);
        return briefs.listByProject(projectId).stream().map(this::toBriefResponse).toList();
    }

    public BriefResponse currentBrief(long userId, long projectId) {
        ProjectRow project = requireProjectOfMyTeam(userId, projectId);
        if (project.getCurrentBriefId() == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toBriefResponse(briefs.findById(project.getCurrentBriefId()));
    }

    // ---------- 内部 ----------

    /**
     * 加载项目并要求它是"我所在团队"的——项目不存在与我不是其团队成员
     * 返回同一个 404（防枚举，与 F1/teamAccess 的语义一致）。
     */
    private ProjectRow requireProjectOfMyTeam(long userId, long projectId) {
        ProjectRow project = projects.findById(projectId);
        if (project == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        teamAccess.requireMember(userId, project.getTeamId());
        return project;
    }

    private ProjectResponse toResponse(ProjectRow row) {
        return new ProjectResponse(row.getId(), row.getName(), row.getDescription(),
                row.getStatus(), row.getCurrentBriefId(), row.getLockVersion());
    }

    private BriefResponse toBriefResponse(BriefRow row) {
        return new BriefResponse(row.getId(), row.getProjectId(), row.getContent(),
                row.getCreatedBy(), row.getCreatedAt().toString());
    }
}
