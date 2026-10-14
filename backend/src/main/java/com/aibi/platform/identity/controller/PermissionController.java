package com.aibi.platform.identity.controller;

import com.aibi.platform.identity.dto.CreatePermissionRequest;
import com.aibi.platform.identity.dto.PermissionData;
import com.aibi.platform.identity.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/permissions", "/api/v1/permissions"})
@Tag(name = "权限字典", description = "平台操作权限字典")
public class PermissionController {

    private final RolePermissionService rolePermissionService;

    public PermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增权限字典项")
    public PermissionData create(
            @RequestHeader(value = "X-Platform-Token", required = false) String authorization,
            @Valid @RequestBody CreatePermissionRequest request) {
        return rolePermissionService.createPermission(authorization, request);
    }

    @GetMapping
    @Operation(summary = "查询权限字典")
    public List<PermissionData> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return rolePermissionService.permissions(authorization);
    }
}
