package com.aibi.platform.identity.controller;

import com.aibi.platform.identity.dto.RegisterRequest;
import com.aibi.platform.identity.dto.LoginData;
import com.aibi.platform.identity.dto.LoginRequest;
import com.aibi.platform.identity.dto.RefreshTokenRequest;
import com.aibi.platform.identity.dto.SwitchTenantRequest;
import com.aibi.platform.identity.dto.TenantContextData;
import com.aibi.platform.identity.dto.UserData;
import com.aibi.platform.identity.service.IdentityService;
import com.aibi.platform.identity.service.PermissionContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@Tag(name = "认证", description = "全局用户注册、登录与会话管理")
public class AuthController {

    private final IdentityService identityService;
    private final PermissionContextService permissionContextService;

    public AuthController(IdentityService identityService, PermissionContextService permissionContextService) {
        this.identityService = identityService;
        this.permissionContextService = permissionContextService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册全局用户")
    public UserData register(@Valid @RequestBody RegisterRequest request) {
        return identityService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "账号密码登录")
    public LoginData login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return identityService.login(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    @Operation(summary = "轮换刷新令牌", description = "旧刷新令牌立即撤销，只能使用一次。")
    public LoginData refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return identityService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "主动退出", description = "立即撤销当前访问令牌对应的会话。")
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        identityService.logout(authorization);
    }

    @PostMapping("/switch-tenant")
    @Operation(summary = "切换企业", description = "验证成员关系并签发带权限版本的租户上下文令牌。")
    public TenantContextData switchTenant(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody SwitchTenantRequest request) {
        return permissionContextService.switchTenant(authorization, request);
    }
}
