package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.AuthorizeWorkspaceMemberRequest;
import com.aibi.platform.identity.dto.CreateWorkspaceRequest;
import com.aibi.platform.identity.dto.UpdateWorkspaceRequest;
import com.aibi.platform.identity.dto.WorkspaceData;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.entity.WorkspaceMemberEntity;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.WorkspaceMapper;
import com.aibi.platform.identity.security.TokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final MemberMapper memberMapper;
    private final PermissionContextService permissionContextService;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public WorkspaceService(WorkspaceMapper workspaceMapper, MemberMapper memberMapper,
            PermissionContextService permissionContextService, TenantActionAuthorizer actions, SecurityAudit audit) {
        this.workspaceMapper = workspaceMapper;
        this.memberMapper = memberMapper;
        this.permissionContextService = permissionContextService;
        this.actions = actions;
        this.audit = audit;
    }

    @MySqlTransactional(timeout = 5)
    public WorkspaceData create(String authorization, long tenantId, CreateWorkspaceRequest request) {
        TokenService.TenantClaims claims = actions.require(authorization, tenantId, "workspace:manage");
        Instant now = Instant.now();
        WorkspaceEntity workspace = workspaceMapper.insert(new WorkspaceEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.workspaceCode(),
                request.workspaceName().trim(), "ACTIVE", claims.memberId(), now, now, 0));
        workspaceMapper.addMember(new WorkspaceMemberEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, workspace.id(),
                claims.memberId(), "ACTIVE"));
        audit.record(claims, "WORKSPACE_CREATED", "WORKSPACE", workspace.id(), claims.tenantPermissionVersion());
        return toData(workspace);
    }

    @MySqlTransactional(timeout = 5)
    public WorkspaceData update(String authorization, long tenantId, long workspaceId,
            UpdateWorkspaceRequest request) {
        var actor = actions.require(authorization, tenantId, "workspace:manage");
        WorkspaceEntity current = required(tenantId, workspaceId);
        WorkspaceEntity updated = new WorkspaceEntity(
                current.id(), current.tenantId(), current.workspaceCode(), request.workspaceName().trim(),
                request.workspaceStatus(), current.ownerMemberId(), current.createdAt(),
                Instant.now(), current.lockVersion() + 1);
        var result = toData(workspaceMapper.update(updated, request.lockVersion()));
        audit.record(actor, "WORKSPACE_UPDATED", "WORKSPACE", workspaceId, actor.tenantPermissionVersion());
        return result;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<WorkspaceData> list(String authorization, long tenantId) {
        var claims = permissionContextService.requireContext(authorization, tenantId);
        return workspaceMapper.findByTenantId(tenantId).stream()
                .filter(value -> workspaceMapper.isActiveMember(tenantId, value.id(), claims.memberId()))
                .map(this::toData).toList();
    }

    @MySqlTransactional(timeout = 5)
    public void authorizeMember(String authorization, long tenantId, long workspaceId,
            AuthorizeWorkspaceMemberRequest request) {
        var actor = actions.require(authorization, tenantId, "workspace:manage");
        WorkspaceEntity workspace = required(tenantId, workspaceId);
        if (!"ACTIVE".equals(workspace.workspaceStatus())) {
            throw new ConflictException("工作空间未处于活动状态");
        }
        MemberEntity member = requiredMember(tenantId, request.memberId());
        if (!"ACTIVE".equals(member.memberStatus())) {
            throw new ConflictException("成员未处于活动状态");
        }
        workspaceMapper.addMember(new WorkspaceMemberEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, workspaceId,
                member.id(), "ACTIVE"));
        bumpMember(member, request.memberLockVersion());
        audit.record(actor, "WORKSPACE_MEMBER_GRANTED", "MEMBER", member.id(), member.permissionVersion() + 1);
    }

    @MySqlTransactional(timeout = 5)
    public void revokeMember(String authorization, long tenantId, long workspaceId,
            long memberId, long memberLockVersion) {
        var actor = actions.require(authorization, tenantId, "workspace:manage");
        WorkspaceEntity workspace = required(tenantId, workspaceId);
        if (workspace.ownerMemberId() == memberId) {
            throw new ForbiddenException();
        }
        MemberEntity member = requiredMember(tenantId, memberId);
        if (!workspaceMapper.removeMember(tenantId, workspaceId, memberId)) {
            throw new NotFoundException("工作空间成员授权不存在");
        }
        bumpMember(member, memberLockVersion);
        audit.record(actor, "WORKSPACE_MEMBER_REVOKED", "MEMBER", member.id(), member.permissionVersion() + 1);
    }

    public WorkspaceEntity required(long tenantId, long workspaceId) {
        return workspaceMapper.findById(tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("工作空间不存在"));
    }

    public boolean isActiveMember(long tenantId, long workspaceId, long memberId) {
        return workspaceMapper.isActiveMember(tenantId, workspaceId, memberId);
    }

    private MemberEntity requiredMember(long tenantId, long memberId) {
        return memberMapper.findById(tenantId, memberId)
                .orElseThrow(() -> new NotFoundException("成员不存在"));
    }

    private void bumpMember(MemberEntity member, long expectedLockVersion) {
        memberMapper.update(new MemberEntity(
                member.id(), member.tenantId(), member.userId(), member.memberName(),
                member.memberStatus(), member.owner(), member.permissionVersion() + 1,
                member.joinedAt(), member.createdAt(), Instant.now(), member.lockVersion() + 1),
                expectedLockVersion);
    }

    private WorkspaceData toData(WorkspaceEntity workspace) {
        return new WorkspaceData(
                workspace.id(), workspace.tenantId(), workspace.workspaceCode(),
                workspace.workspaceName(), workspace.workspaceStatus(), workspace.ownerMemberId(),
                workspace.lockVersion());
    }
}
