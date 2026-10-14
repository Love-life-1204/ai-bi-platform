package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.AssignMemberRoleRequest;
import com.aibi.platform.identity.dto.MemberRoleData;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberRoleService {

    private final AccessControlMapper accessControlMapper;
    private final MemberMapper memberMapper;
    private final PermissionContextService permissionContextService;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final com.aibi.platform.identity.mapper.WorkspaceMapper workspaces;
    private final SecureRandom secureRandom = new SecureRandom();

    public MemberRoleService(AccessControlMapper accessControlMapper, MemberMapper memberMapper,
            PermissionContextService permissionContextService, TenantActionAuthorizer actions,
            com.aibi.platform.identity.mapper.WorkspaceMapper workspaces, SecurityAudit audit) {
        this.accessControlMapper = accessControlMapper;
        this.memberMapper = memberMapper;
        this.permissionContextService = permissionContextService;
        this.actions = actions;
        this.audit = audit;
        this.workspaces = workspaces;
    }

    @MySqlTransactional(timeout = 5)
    public MemberRoleData assign(String authorization, long tenantId, long memberId,
            AssignMemberRoleRequest request) {
        var actor = actions.require(authorization, tenantId, "role:manage");
        actions.requireDelegable(tenantId, actor.memberId(), request.workspaceId(), request.roleId());
        MemberEntity member = requiredMember(tenantId, memberId);
        requireOwnerManagement(actor.memberId(), member);
        if (request.workspaceId() != null) {
            var workspace = workspaces.findById(tenantId, request.workspaceId())
                    .orElseThrow(com.aibi.platform.common.exception.ForbiddenException::new);
            if (!"ACTIVE".equals(workspace.workspaceStatus())
                    || !workspaces.isActiveMember(tenantId, workspace.id(), actor.memberId())
                    || !workspaces.isActiveMember(tenantId, workspace.id(), memberId)) {
                throw new com.aibi.platform.common.exception.ForbiddenException();
            }
        }
        if (!"ACTIVE".equals(member.memberStatus())) {
            throw new ConflictException("只能向活动成员分配角色");
        }
        RoleEntity role = requiredRole(tenantId, request.roleId());
        if (!"ACTIVE".equals(role.roleStatus())) {
            throw new ConflictException("角色未处于活动状态");
        }
        String scopeKey = request.workspaceId() == null
                ? "TENANT" : "WORKSPACE:" + request.workspaceId();
        MemberRoleEntity assignment = accessControlMapper.insertMemberRole(new MemberRoleEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, memberId, role.id(),
                request.workspaceId(), scopeKey, request.expiresAt()));
        bumpMemberPermissionVersion(member, request.memberLockVersion());
        audit.record(actor, "MEMBER_ROLE_ASSIGNED", "MEMBER", memberId, member.permissionVersion() + 1);
        return toData(assignment, role, Instant.now());
    }

    @MySqlTransactional(timeout = 5)
    public void revoke(String authorization, long tenantId, long memberId,
            long assignmentId, long memberLockVersion) {
        var actor = actions.require(authorization, tenantId, "role:manage");
        MemberEntity member = requiredMember(tenantId, memberId);
        requireOwnerManagement(actor.memberId(), member);
        MemberRoleEntity assignment = accessControlMapper.findMemberRole(tenantId, assignmentId)
                .filter(value -> value.memberId() == memberId)
                .orElseThrow(() -> new NotFoundException("成员角色分配不存在"));
        if (!accessControlMapper.revokeMemberRole(tenantId, assignment.id())) {
            throw new ConflictException("成员角色已撤销");
        }
        bumpMemberPermissionVersion(member, memberLockVersion);
        audit.record(actor, "MEMBER_ROLE_REVOKED", "MEMBER", memberId, member.permissionVersion() + 1);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<MemberRoleData> list(String authorization, long tenantId, long memberId) {
        permissionContextService.requireContext(authorization, tenantId);
        requiredMember(tenantId, memberId);
        Instant now = Instant.now();
        return accessControlMapper.findMemberRoles(tenantId, memberId).stream()
                .map(value -> toData(value, requiredRole(tenantId, value.roleId()), now)).toList();
    }

    public List<MemberRoleEntity> effectiveAssignments(long tenantId, long memberId,
            Long workspaceId, Instant now) {
        return accessControlMapper.findMemberRoles(tenantId, memberId).stream()
                .filter(value -> value.isEffectiveAt(now))
                .filter(value -> value.workspaceId() == null || value.workspaceId().equals(workspaceId))
                .filter(value -> accessControlMapper.findRole(tenantId, value.roleId())
                        .map(role -> "ACTIVE".equals(role.roleStatus())).orElse(false))
                .toList();
    }

    private void bumpMemberPermissionVersion(MemberEntity member, long expectedLockVersion) {
        MemberEntity updated = new MemberEntity(
                member.id(), member.tenantId(), member.userId(), member.memberName(),
                member.memberStatus(), member.owner(), member.permissionVersion() + 1,
                member.joinedAt(), member.createdAt(), Instant.now(), member.lockVersion() + 1);
        memberMapper.update(updated, expectedLockVersion);
    }

    private void requireOwnerManagement(long actorMemberId, MemberEntity target) {
        if (target.owner() && !requiredMember(target.tenantId(), actorMemberId).owner()) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
    }

    private MemberEntity requiredMember(long tenantId, long memberId) {
        return memberMapper.findById(tenantId, memberId)
                .orElseThrow(() -> new NotFoundException("成员不存在"));
    }

    private RoleEntity requiredRole(long tenantId, long roleId) {
        return accessControlMapper.findRole(tenantId, roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
    }

    private MemberRoleData toData(MemberRoleEntity assignment, RoleEntity role, Instant now) {
        return new MemberRoleData(
                assignment.id(), assignment.tenantId(), assignment.memberId(), assignment.roleId(),
                role.roleCode(), assignment.workspaceId(), assignment.scopeKey(), assignment.expiresAt(),
                assignment.isEffectiveAt(now) && "ACTIVE".equals(role.roleStatus()));
    }
}
