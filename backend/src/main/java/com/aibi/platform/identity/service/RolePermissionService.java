package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.CreatePermissionRequest;
import com.aibi.platform.identity.dto.CreateRoleRequest;
import com.aibi.platform.identity.dto.PermissionData;
import com.aibi.platform.identity.dto.RoleData;
import com.aibi.platform.identity.dto.RolePermissionData;
import com.aibi.platform.identity.dto.SetRolePermissionRequest;
import com.aibi.platform.identity.dto.UpdateRoleRequest;
import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.entity.RolePermissionEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.manager.TenantManager;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class RolePermissionService {

    private final AccessControlMapper accessControlMapper;
    private final PermissionContextService permissionContextService;
    private final SessionService sessionService;
    private final TenantManager tenantManager;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final com.aibi.platform.commercial.service.CommercialAccessService platformAccess;
    private final SecureRandom secureRandom = new SecureRandom();

    public RolePermissionService(AccessControlMapper accessControlMapper,
            PermissionContextService permissionContextService, SessionService sessionService,
            TenantManager tenantManager, TenantActionAuthorizer actions,
            com.aibi.platform.commercial.service.CommercialAccessService platformAccess, SecurityAudit audit) {
        this.accessControlMapper = accessControlMapper;
        this.permissionContextService = permissionContextService;
        this.sessionService = sessionService;
        this.tenantManager = tenantManager;
        this.actions = actions;
        this.audit = audit;
        this.platformAccess = platformAccess;
    }

    @MySqlTransactional(timeout = 5)
    public PermissionData createPermission(String authorization, CreatePermissionRequest request) {
        platformAccess.requirePlatform(authorization);
        PermissionEntity permission = new PermissionEntity(
                secureRandom.nextLong(1000, Long.MAX_VALUE), request.permissionCode(),
                request.permissionName().trim(), request.resourceType().toUpperCase(Locale.ROOT),
                request.actionCode().toLowerCase(Locale.ROOT), 0);
        return toData(accessControlMapper.insertPermission(permission));
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<PermissionData> permissions(String authorization) {
        sessionService.authenticate(authorization);
        return accessControlMapper.findPermissions().stream().map(this::toData).toList();
    }

    @MySqlTransactional(timeout = 5)
    public RoleData createRole(String authorization, long tenantId, CreateRoleRequest request) {
        var actor = actions.require(authorization, tenantId, "role:manage");
        var template = RoleTemplateCatalog.permissions(request.roleCode());
        for (String code : template) {
            actions.requireForMember(tenantId, actor.memberId(), null, code);
        }
        RoleEntity role = new RoleEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.roleCode(),
                request.roleName().trim(), "ACTIVE", !template.isEmpty(), 0);
        var created = accessControlMapper.insertRole(role);
        for (String code : template) {
            var permission = accessControlMapper.findPermissionByCode(code)
                    .orElseThrow(() -> new NotFoundException("角色模板权限尚未迁移"));
            accessControlMapper.setRolePermission(new RolePermissionEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                    tenantId, created.id(), permission.id(), "ALLOW"));
        }
        audit.record(actor, "ROLE_CREATED", "ROLE", created.id(), actor.tenantPermissionVersion());
        return toData(created);
    }

    @MySqlTransactional(timeout = 5)
    public RoleData updateRole(String authorization, long tenantId, long roleId,
            UpdateRoleRequest request) {
        var actor = actions.require(authorization, tenantId, "role:manage");
        RoleEntity current = requiredRole(tenantId, roleId);
        if (current.builtin()) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
        RoleEntity updated = new RoleEntity(
                current.id(), current.tenantId(), current.roleCode(), request.roleName().trim(),
                request.roleStatus(), current.builtin(), current.lockVersion() + 1);
        RoleData result = toData(accessControlMapper.updateRole(updated, request.lockVersion()));
        var changed = tenantManager.incrementPermissionVersion(tenantId);
        audit.record(actor, "ROLE_UPDATED", "ROLE", roleId, changed.permissionVersion());
        return result;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<RoleData> roles(String authorization, long tenantId) {
        permissionContextService.requireContext(authorization, tenantId);
        return accessControlMapper.findRoles(tenantId).stream().map(this::toData).toList();
    }

    @MySqlTransactional(timeout = 5)
    public RolePermissionData setRolePermission(String authorization, long tenantId, long roleId,
            SetRolePermissionRequest request) {
        var actor = actions.require(authorization, tenantId, "role:manage");
        if (requiredRole(tenantId, roleId).builtin()) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
        PermissionEntity permission = accessControlMapper.findPermission(request.permissionId())
                .orElseThrow(() -> new NotFoundException("权限不存在"));
        if ("ALLOW".equals(request.effect())) {
            actions.requireForMember(tenantId, actor.memberId(), null, permission.permissionCode());
        }
        RolePermissionEntity value = accessControlMapper.setRolePermission(new RolePermissionEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, roleId,
                permission.id(), request.effect()));
        var changed = tenantManager.incrementPermissionVersion(tenantId);
        audit.record(actor, "ROLE_PERMISSION_CHANGED", "ROLE", roleId, changed.permissionVersion());
        return new RolePermissionData(roleId, permission.id(), permission.permissionCode(), value.effect());
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<RolePermissionData> rolePermissions(String authorization, long tenantId, long roleId) {
        permissionContextService.requireContext(authorization, tenantId);
        requiredRole(tenantId, roleId);
        return accessControlMapper.findRolePermissions(tenantId, roleId).stream()
                .map(value -> {
                    PermissionEntity permission = accessControlMapper.findPermission(value.permissionId())
                            .orElseThrow(() -> new NotFoundException("权限不存在"));
                    return new RolePermissionData(
                            roleId, permission.id(), permission.permissionCode(), value.effect());
                }).toList();
    }

    private RoleEntity requiredRole(long tenantId, long roleId) {
        return accessControlMapper.findRole(tenantId, roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
    }

    private PermissionData toData(PermissionEntity permission) {
        return new PermissionData(
                permission.id(), permission.permissionCode(), permission.permissionName(),
                permission.resourceType(), permission.actionCode(), permission.lockVersion());
    }

    private RoleData toData(RoleEntity role) {
        return new RoleData(
                role.id(), role.tenantId(), role.roleCode(), role.roleName(),
                role.roleStatus(), role.builtin(), role.lockVersion());
    }
}
