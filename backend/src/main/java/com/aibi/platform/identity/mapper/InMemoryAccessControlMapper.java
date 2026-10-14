package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.entity.RolePermissionEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAccessControlMapper implements AccessControlMapper {

    private final ConcurrentMap<Long, PermissionEntity> permissions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> permissionIdsByCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, RoleEntity> roles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> roleIdsByCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RolePermissionEntity> rolePermissions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, MemberRoleEntity> memberRoles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> memberRoleIdsByScope = new ConcurrentHashMap<>();

    public InMemoryAccessControlMapper() {
        seedPermission(1, "tenant:manage", "管理企业", "TENANT", "manage");
        seedPermission(2, "member:manage", "管理成员", "MEMBER", "manage");
        seedPermission(3, "workspace:manage", "管理空间", "WORKSPACE", "manage");
        seedPermission(4, "role:manage", "管理角色", "ROLE", "manage");
        seedPermission(5, "policy:manage", "管理数据权限", "POLICY", "manage");
        seedPermission(6, "datasource:manage", "管理数据源", "DATASOURCE", "manage");
        seedPermission(7, "dataset:read", "读取数据集", "DATASET", "read");
        seedPermission(8, "dataset:manage", "管理数据集", "DATASET", "manage");
        seedPermission(9, "dataset:publish", "发布数据集", "DATASET", "publish");
        seedPermission(10, "metric:read", "读取指标", "METRIC", "read");
        seedPermission(11, "metric:manage", "管理指标", "METRIC", "manage");
        seedPermission(12, "query:execute", "受控查询", "QUERY", "execute");
        seedPermission(13, "chart:manage", "管理图表", "CHART", "manage");
        seedPermission(14, "dashboard:read", "查看看板", "DASHBOARD", "read");
        seedPermission(15, "dashboard:manage", "管理看板", "DASHBOARD", "manage");
        seedPermission(16, "report:export", "导出报告", "REPORT", "export");
        seedPermission(17, "ai:ask", "AI问答", "AI", "ask");
        seedPermission(18, "audit:read", "查看租户审计", "AUDIT", "read");
        seedPermission(19, "subscription:manage", "管理订阅", "SUBSCRIPTION", "manage");
        seedPermission(20, "tenant:exit", "申请企业退出", "TENANT", "exit");
        seedPermission(22, "import:manage", "管理文件导入", "IMPORT", "manage");
        seedPermission(24, "sync:manage", "管理数据同步", "SYNC", "manage");
        seedPermission(25, "quality:read", "查看数据质量", "QUALITY", "read");
        seedPermission(26, "quality:manage", "管理数据质量", "QUALITY", "manage");
        for (var action : com.aibi.platform.identity.service.ResourceActionCatalog.DEFINITIONS) {
            seedPermission(action.id(), action.code(), action.name(), action.resourceType(), action.action());
        }
    }

    @Override
    public PermissionEntity insertPermission(PermissionEntity permission) {
        if (permissionIdsByCode.putIfAbsent(permission.permissionCode(), permission.id()) != null
                || permissions.putIfAbsent(permission.id(), permission) != null) {
            permissionIdsByCode.remove(permission.permissionCode(), permission.id());
            throw new ConflictException("权限编码已存在");
        }
        return permission;
    }

    @Override
    public Optional<PermissionEntity> findPermission(long permissionId) {
        return Optional.ofNullable(permissions.get(permissionId));
    }

    @Override
    public Optional<PermissionEntity> findPermissionByCode(String permissionCode) {
        Long id = permissionIdsByCode.get(permissionCode);
        return id == null ? Optional.empty() : findPermission(id);
    }

    @Override
    public List<PermissionEntity> findPermissions() {
        return permissions.values().stream()
                .sorted(Comparator.comparing(PermissionEntity::permissionCode)).toList();
    }

    @Override
    public RoleEntity insertRole(RoleEntity role) {
        String key = roleKey(role.tenantId(), role.roleCode());
        if (roleIdsByCode.putIfAbsent(key, role.id()) != null
                || roles.putIfAbsent(role.id(), role) != null) {
            roleIdsByCode.remove(key, role.id());
            throw new ConflictException("角色编码已存在");
        }
        return role;
    }

    @Override
    public Optional<RoleEntity> findRole(long tenantId, long roleId) {
        return Optional.ofNullable(roles.get(roleId)).filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<RoleEntity> findRoles(long tenantId) {
        return roles.values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(RoleEntity::roleCode)).toList();
    }

    @Override
    public RoleEntity updateRole(RoleEntity role, long expectedLockVersion) {
        return roles.compute(role.id(), (id, current) -> {
            if (current == null || current.tenantId() != role.tenantId()) {
                throw new NotFoundException("角色不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return role;
        });
    }

    @Override
    public RolePermissionEntity setRolePermission(RolePermissionEntity rolePermission) {
        rolePermissions.put(rolePermissionKey(
                rolePermission.tenantId(), rolePermission.roleId(), rolePermission.permissionId()),
                rolePermission);
        return rolePermission;
    }

    @Override
    public List<RolePermissionEntity> findRolePermissions(long tenantId, long roleId) {
        return rolePermissions.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.roleId() == roleId)
                .sorted(Comparator.comparingLong(RolePermissionEntity::permissionId)).toList();
    }

    @Override
    public MemberRoleEntity insertMemberRole(MemberRoleEntity value) {
        String key = memberRoleKey(value.tenantId(), value.memberId(), value.roleId(), value.scopeKey());
        if (memberRoleIdsByScope.putIfAbsent(key, value.id()) != null
                || memberRoles.putIfAbsent(value.id(), value) != null) {
            memberRoleIdsByScope.remove(key, value.id());
            throw new ConflictException("成员角色已分配");
        }
        return value;
    }

    @Override
    public Optional<MemberRoleEntity> findMemberRole(long tenantId, long assignmentId) {
        return Optional.ofNullable(memberRoles.get(assignmentId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<MemberRoleEntity> findMemberRoles(long tenantId, long memberId) {
        return memberRoles.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.memberId() == memberId)
                .sorted(Comparator.comparingLong(MemberRoleEntity::id)).toList();
    }

    @Override
    public boolean revokeMemberRole(long tenantId, long assignmentId) {
        MemberRoleEntity current = findMemberRole(tenantId, assignmentId).orElse(null);
        if (current == null || !memberRoles.remove(assignmentId, current)) {
            return false;
        }
        memberRoleIdsByScope.remove(memberRoleKey(
                tenantId, current.memberId(), current.roleId(), current.scopeKey()), assignmentId);
        return true;
    }

    private void seedPermission(long id, String code, String name, String resource, String action) {
        PermissionEntity permission = new PermissionEntity(id, code, name, resource, action, 0);
        permissions.put(id, permission);
        permissionIdsByCode.put(code, id);
    }

    private String roleKey(long tenantId, String code) {
        return tenantId + ":" + code;
    }

    private String rolePermissionKey(long tenantId, long roleId, long permissionId) {
        return tenantId + ":" + roleId + ":" + permissionId;
    }

    private String memberRoleKey(long tenantId, long memberId, long roleId, String scopeKey) {
        return tenantId + ":" + memberId + ":" + roleId + ":" + scopeKey;
    }
}
