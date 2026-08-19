package com.frameflow.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.identity.application.IdempotencyExecution;
import com.frameflow.identity.application.TeamService;
import com.frameflow.identity.security.CurrentUser;
import com.frameflow.identity.web.dto.AddMemberRequest;
import com.frameflow.identity.web.dto.CreateTeamRequest;
import com.frameflow.identity.web.dto.MemberList;
import com.frameflow.identity.web.dto.Team;
import com.frameflow.identity.web.dto.TeamList;
import com.frameflow.identity.web.dto.TeamMember;
import com.frameflow.identity.web.dto.UpdateMemberRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 团队与成员管理（permission-matrix.md §2）；团队写接口以 PostgreSQL 为幂等事实源。 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "teams", description = "团队与成员管理（OWNER 管理操作）")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;
    private final ObjectMapper objectMapper;

    public TeamController(TeamService teamService, ObjectMapper objectMapper) {
        this.teamService = teamService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(operationId = "listMyTeams", summary = "发现当前用户加入的有效团队",
            description = "仅返回当前登录用户 status=ACTIVE 的团队成员关系，用于登录后的团队选择；不会返回其他用户或已移除的成员关系。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "当前用户的团队成员关系列表",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TeamList.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public TeamList listMyTeams(Authentication authentication) {
        return teamService.listMyTeams(currentUserId(authentication));
    }

    @PostMapping
    @Operation(operationId = "createTeam", summary = "创建团队（创建者自动成为 OWNER；需要 Idempotency-Key）",
            description = "任一 ACTIVE 已认证用户均可创建；团队与创建者 OWNER 关系在同一事务写入。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "团队创建成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Team.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或缺少 Idempotency-Key",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_CONFLICT / IDEMPOTENCY_IN_PROGRESS",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public ResponseEntity<Team> createTeam(
            Authentication authentication,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string", format = "uuid", maxLength = 128))
            @RequestHeader(value = "Idempotency-Key", required = true) UUID idempotencyKey,
            @Valid @RequestBody CreateTeamRequest request) throws Exception {
        IdempotencyExecution<TeamService.CreateOutcome> execution =
                teamService.createTeam(currentUserId(authentication), request, idempotencyKey);
        if (execution.isReplayed()) {
            Team team = objectMapper.readValue(execution.replayBody(), Team.class);
            return ResponseEntity.status(execution.replayStatus()).contentType(MediaType.APPLICATION_JSON).body(team);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(execution.value().dto());
    }

    @GetMapping("/{teamId}")
    @Operation(operationId = "getTeam", summary = "查看团队（仅团队成员；非成员统一 404）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "团队信息",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Team.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "团队不存在或无权访问",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public Team getTeam(Authentication authentication, @PathVariable long teamId) {
        return teamService.getTeam(currentUserId(authentication), teamId);
    }

    @GetMapping("/{teamId}/members")
    @Operation(operationId = "listTeamMembers", summary = "团队 ACTIVE 成员列表（仅团队成员）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成员列表",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberList.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "团队不存在或无权访问",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public MemberList listTeamMembers(Authentication authentication, @PathVariable long teamId) {
        return teamService.listMembers(currentUserId(authentication), teamId);
    }

    @PostMapping("/{teamId}/members")
    @Operation(operationId = "addTeamMember", summary = "直接添加已注册成员为 ACTIVE（仅 OWNER；需要 Idempotency-Key）",
            description = "已是 ACTIVE 成员返回 409 TEAM_MEMBER_ALREADY_EXISTS；REMOVED 关系则复用原记录并恢复为 ACTIVE。M01 不创建 INVITED 记录。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "成员已添加",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TeamMember.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或缺少 Idempotency-Key",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "已认证但角色不足（非 OWNER）",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "团队或目标用户不存在/无权访问",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "TEAM_MEMBER_ALREADY_EXISTS / 幂等冲突",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public ResponseEntity<TeamMember> addTeamMember(
            Authentication authentication,
            @PathVariable long teamId,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string", format = "uuid", maxLength = 128))
            @RequestHeader(value = "Idempotency-Key", required = true) UUID idempotencyKey,
            @Valid @RequestBody AddMemberRequest request) throws Exception {
        IdempotencyExecution<TeamService.MemberOutcome> execution =
                teamService.addMember(currentUserId(authentication), teamId, request, idempotencyKey);
        if (execution.isReplayed()) {
            TeamMember member = objectMapper.readValue(execution.replayBody(), TeamMember.class);
            return ResponseEntity.status(execution.replayStatus()).contentType(MediaType.APPLICATION_JSON).body(member);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(execution.value().dto());
    }

    @PatchMapping("/{teamId}/members/{userId}")
    @Operation(operationId = "updateMemberRole", summary = "修改成员角色（仅 OWNER；不能把最后一名 OWNER 降级）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "角色已更新",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TeamMember.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "已认证但角色不足（非 OWNER）",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "团队或成员不存在/无权访问",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "TEAM_LAST_OWNER_CONFLICT 等",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public TeamMember updateMemberRole(
            Authentication authentication, @PathVariable long teamId, @PathVariable long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return teamService.updateRole(currentUserId(authentication), teamId, userId, request);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @Operation(operationId = "removeTeamMember", summary = "移除成员（仅 OWNER；不能移除自己或最后一名 OWNER）")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "成员已移除"),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "已认证但角色不足（非 OWNER）",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "团队或成员不存在/无权访问",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "TEAM_SELF_REMOVAL_FORBIDDEN / TEAM_LAST_OWNER_CONFLICT",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public ResponseEntity<Void> removeTeamMember(
            Authentication authentication, @PathVariable long teamId, @PathVariable long userId) {
        teamService.removeMember(currentUserId(authentication), teamId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static long currentUserId(Authentication authentication) {
        return ((CurrentUser) authentication.getPrincipal()).userId();
    }
}
