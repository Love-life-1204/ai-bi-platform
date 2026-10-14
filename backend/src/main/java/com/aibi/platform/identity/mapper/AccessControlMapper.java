package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.entity.RolePermissionEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import java.util.List;
import java.util.Optional;

public interface AccessControlMapper {

    PermissionEntity insertPermission(PermissionEntity permission);

    Optional<PermissionEntity> findPermission(long permissionId);

    Optional<PermissionEntity> findPermissionByCode(String permissionCode);

    List<PermissionEntity> findPermissions();

    RoleEntity insertRole(RoleEntity role);

    Optional<RoleEntity> findRole(long tenantId, long roleId);

    List<RoleEntity> findRoles(long tenantId);

    RoleEntity updateRole(RoleEntity role, long expectedLockVersion);

    RolePermissionEntity setRolePermission(RolePermissionEntity rolePermission);

    List<RolePermissionEntity> findRolePermissions(long tenantId, long roleId);

    MemberRoleEntity insertMemberRole(MemberRoleEntity memberRole);

    Optional<MemberRoleEntity> findMemberRole(long tenantId, long assignmentId);

    List<MemberRoleEntity> findMemberRoles(long tenantId, long memberId);

    boolean revokeMemberRole(long tenantId, long assignmentId);
}
