package com.frameflow.identity.web;

import com.frameflow.identity.application.AccessTokenService;
import com.frameflow.identity.application.RefreshTokenService;
import com.frameflow.identity.application.UserService;
import com.frameflow.identity.security.CurrentUser;
import com.frameflow.identity.web.dto.LoginRequest;
import com.frameflow.identity.web.dto.RefreshRequest;
import com.frameflow.identity.web.dto.RegisterRequest;
import com.frameflow.identity.web.dto.TokenPair;
import com.frameflow.identity.web.dto.TokenType;
import com.frameflow.identity.web.dto.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 注册、登录、Token 生命周期（token-contract.md §4）。 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "注册、登录、Token 生命周期")
public class AuthController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;

    public AuthController(UserService userService, RefreshTokenService refreshTokenService,
                          AccessTokenService accessTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
    }

    @PostMapping("/register")
    @Operation(operationId = "registerUser", summary = "用户注册",
            description = "规范化邮箱已注册时返回 409 USER_EMAIL_CONFLICT。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "注册成功，返回用户信息",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "邮箱已被注册",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "登录，返回 Token 对")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登录成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPair.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "邮箱或密码错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public TokenPair login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshToken", summary = "刷新 Token（轮换，旧 Refresh Token 立即失效）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "新 Token 对",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPair.class))),
        @ApiResponse(responseCode = "401", description = "Refresh Token 无效/过期/重放",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.RefreshedToken refreshed =
                refreshTokenService.refresh(request.refreshToken());
        String accessToken = accessTokenService.issue(refreshed.userId());
        return new TokenPair(accessToken, refreshed.refreshToken(),
                accessTokenService.expiresInSeconds(), TokenType.Bearer);
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "登出，撤销 Refresh Token",
            description = "Refresh Token 是唯一凭据，不要求 Access Token；即使 Access Token 已过期，仍可撤销该设备 token family。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "登出成功"),
        @ApiResponse(responseCode = "401", description = "Refresh Token 无效/过期/重放",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    @Operation(operationId = "currentUser", summary = "当前登录用户")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "当前用户信息",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "401", description = "未认证或 Token 无效/过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.frameflow.identity.error.ErrorResponse.class)))
    })
    public User me(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return userService.me(currentUser.userId());
    }
}
