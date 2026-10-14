package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.security.TokenService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 管理动作只信任当前数据库成员和有效角色，避免权限服务之间循环依赖。 */
@Component
public class TenantActionAuthorizer {

    private static final Set<String> OWNER_ACTIONS = Set.of(
            "tenant:manage", "member:manage", "role:manage", "workspace:manage", "policy:manage");
    private final PermissionContextService contexts;
    private final MemberMapper members;
    private final AccessControlMapper access;

    public TenantActionAuthorizer(PermissionContextService contexts, MemberMapper members,
            AccessControlMapper access) {
        this.contexts = contexts;
        this.members = members;
        this.access = access;
    }

    public TokenService.TenantClaims require(String authorization, long tenantId, String action) {
        TokenService.TenantClaims claims = contexts.requireContext(authorization, tenantId);
        requireForMember(tenantId, claims.memberId(), null, action);
        return claims;
    }

    public void requireForMember(long tenantId, long memberId, Long workspaceId, String action) {
        if (!allowed(tenantId, memberId, workspaceId, action)) {
            throw new ForbiddenException();
        }
    }

    public boolean allowed(long tenantId, long memberId, Long workspaceId, String action) {
        var member = members.findById(tenantId, memberId)
                .filter(value -> "ACTIVE".equals(value.memberStatus())).orElse(null);
        PermissionEntity permission = access.findPermissionByCode(action).orElse(null);
        String alias = ResourceActionCatalog.legacyAlias(action);
        var legacy = alias == null ? null : access.findPermissionByCode(alias).orElse(null);
        if (member == null || (permission == null && legacy == null)) {
            return false;
        }
        Instant now = Instant.now();
        List<MemberRoleEntity> roles = access.findMemberRoles(tenantId, memberId).stream()
                .filter(value -> value.isEffectiveAt(now))
                .filter(value -> value.workspaceId() == null || value.workspaceId().equals(workspaceId))
                .filter(value -> access.findRole(tenantId, value.roleId())
                        .map(role -> "ACTIVE".equals(role.roleStatus())).orElse(false)).toList();
        var grants = roles.stream().flatMap(role -> access.findRolePermissions(tenantId, role.roleId()).stream())
                .filter(value -> (permission != null && value.permissionId() == permission.id())
                        || (legacy != null && value.permissionId() == legacy.id())).toList();
        if (grants.stream().anyMatch(value -> "DENY".equals(value.effect()))) {
            return false;
        }
        return grants.stream().anyMatch(value -> "ALLOW".equals(value.effect()))
                || (workspaceId == null && member.owner() && OWNER_ACTIONS.contains(action));
    }

    public boolean explicitlyDenied(long tenantId, long memberId, Long workspaceId, String action) {
        var definition = access.findPermissionByCode(action).orElse(null);
        String alias = ResourceActionCatalog.legacyAlias(action);
        var legacy = alias == null ? null : access.findPermissionByCode(alias).orElse(null);
        Instant now = Instant.now();
        return access.findMemberRoles(tenantId, memberId).stream()
                .filter(value -> value.isEffectiveAt(now))
                .filter(value -> value.workspaceId() == null || value.workspaceId().equals(workspaceId))
                .filter(value -> access.findRole(tenantId, value.roleId())
                        .map(role -> "ACTIVE".equals(role.roleStatus())).orElse(false))
                .flatMap(role -> access.findRolePermissions(tenantId, role.roleId()).stream())
                .anyMatch(grant -> "DENY".equals(grant.effect())
                        && ((definition != null && grant.permissionId() == definition.id())
                        || (legacy != null && grant.permissionId() == legacy.id())));
    }

    /** 不能把只能管理角色的管理员提升成租户权限委派者。 */
    public void requireDelegable(long tenantId, long memberId, Long workspaceId, long roleId) {
        var role = access.findRole(tenantId, roleId).orElseThrow(ForbiddenException::new);
        if (!"ACTIVE".equals(role.roleStatus())) {
            throw new ForbiddenException();
        }
        for (var grant : access.findRolePermissions(tenantId, roleId)) {
            if ("ALLOW".equals(grant.effect())) {
                String code = access.findPermission(grant.permissionId())
                        .orElseThrow(ForbiddenException::new).permissionCode();
                requireForMember(tenantId, memberId, workspaceId, code);
            }
        }
    }
}
