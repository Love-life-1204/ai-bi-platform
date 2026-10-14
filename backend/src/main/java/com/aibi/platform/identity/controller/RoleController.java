package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.CreateRoleRequest;
import com.aibi.platform.identity.dto.RoleData;
import com.aibi.platform.identity.dto.RolePermissionData;
import com.aibi.platform.identity.dto.SetRolePermissionRequest;
import com.aibi.platform.identity.dto.UpdateRoleRequest;
import com.aibi.platform.identity.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/roles", "/api/v1/tenants/{tenantId}/roles"})
@Tag(name = "角色", description = "租户角色及角色权限管理")
public class RoleController {

    private final RolePermissionService rolePermissionService;

    public RoleController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增角色")
    public RoleData create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateRoleRequest request) {
        return rolePermissionService.createRole(authorization, tenantId, request);
    }

    @GetMapping
    @Operation(summary = "查询角色")
    public List<RoleData> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId) {
        return rolePermissionService.roles(authorization, tenantId);
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "修改角色")
    public RoleData update(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        return rolePermissionService.updateRole(authorization, tenantId, roleId, request);
    }

    @PostMapping("/{roleId}/permissions")
    @Operation(summary = "设置角色权限", description = "同一权限重复设置时更新 ALLOW/DENY 效果。")
    public RolePermissionData setPermission(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long roleId,
            @Valid @RequestBody SetRolePermissionRequest request) {
        return rolePermissionService.setRolePermission(authorization, tenantId, roleId, request);
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "查询角色权限")
    public List<RolePermissionData> permissions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long roleId) {
        return rolePermissionService.rolePermissions(authorization, tenantId, roleId);
    }
}
