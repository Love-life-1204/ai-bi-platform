package com.aibi.platform.identity.service;

import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.CreateInvitationRequest;
import com.aibi.platform.identity.dto.InvitationData;
import com.aibi.platform.identity.dto.AcceptInvitationRequest;
import com.aibi.platform.identity.dto.MemberData;
import com.aibi.platform.identity.entity.InvitationEntity;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.identity.entity.WorkspaceMemberEntity;
import com.aibi.platform.identity.mapper.WorkspaceMapper;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.InvitationMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.manager.TenantManager;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InvitationService {

    private final InvitationMapper invitationMapper;
    private final PermissionContextService permissionContextService;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final UserMapper userMapper;
    private final MemberMapper memberMapper;
    private final TenantManager tenantManager;
    private final TenantActionAuthorizer actions;
    private final WorkspaceMapper workspaces;
    private final AccessControlMapper accessControl;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationService(InvitationMapper invitationMapper,
            PermissionContextService permissionContextService, TokenService tokenService,
            SessionService sessionService, UserMapper userMapper, MemberMapper memberMapper,
            TenantManager tenantManager, TenantActionAuthorizer actions,
            WorkspaceMapper workspaces, AccessControlMapper accessControl) {
        this.invitationMapper = invitationMapper;
        this.permissionContextService = permissionContextService;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.userMapper = userMapper;
        this.memberMapper = memberMapper;
        this.tenantManager = tenantManager;
        this.actions = actions;
        this.workspaces = workspaces;
        this.accessControl = accessControl;
    }

    @MySqlTransactional(timeout = 5)
    public InvitationData create(String authorization, long tenantId, CreateInvitationRequest request) {
        TokenService.TenantClaims claims = actions.require(authorization, tenantId, "member:manage");
        validateScope(tenantId, claims.memberId(), request.workspaceId(), request.roleId());
        String rawToken = tokenService.newOpaqueToken();
        Instant now = Instant.now();
        InvitationEntity invitation = new InvitationEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId,
                request.email().trim().toLowerCase(Locale.ROOT), tokenService.hash(rawToken),
                claims.memberId(), request.workspaceId(), request.roleId(),
                now.plus(Duration.ofHours(request.expiresInHours())), null, "PENDING", now, now, 0);
        InvitationEntity created = invitationMapper.insert(invitation);
        return toData(created, rawToken);
    }

    @MySqlTransactional(timeout = 5)
    public MemberData accept(String authorization, AcceptInvitationRequest request) {
        TokenService.AccessClaims access = sessionService.authenticate(authorization);
        var user = userMapper.findById(access.userId()).orElseThrow(ForbiddenException::new);
        Instant now = Instant.now();
        InvitationEntity invitation = invitationMapper
                .findByTokenHash(tokenService.hash(request.inviteToken()))
                .orElseThrow(ForbiddenException::new);
        if (!"PENDING".equals(invitation.invitationStatus())
                || !invitation.expiresAt().isAfter(now)) {
            throw new ConflictException("邀请已使用或已过期");
        }
        if (!invitation.email().equals(user.email())
                || !"ACTIVE".equals(tenantManager.getRequired(invitation.tenantId()).tenantStatus())) {
            throw new ForbiddenException();
        }
        if (memberMapper.findByTenantAndUser(invitation.tenantId(), user.id()).isPresent()) {
            throw new ConflictException("用户已是该租户成员");
        }
        actions.requireForMember(invitation.tenantId(), invitation.inviterMemberId(), null, "member:manage");
        validateScope(invitation.tenantId(), invitation.inviterMemberId(), invitation.workspaceId(), invitation.roleId());
        MemberEntity member = new MemberEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), invitation.tenantId(), user.id(),
                request.memberName().trim(), "ACTIVE", false, 1, now, now, now, 0);
        MemberEntity created = memberMapper.insert(member);
        if (!invitationMapper.accept(invitation.id(), now, invitation.lockVersion())) {
            throw new ConflictException("邀请已使用或已过期");
        }
        if (invitation.workspaceId() != null) {
            workspaces.addMember(new WorkspaceMemberEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                    invitation.tenantId(), invitation.workspaceId(), created.id(), "ACTIVE"));
        }
        if (invitation.roleId() != null) {
            accessControl.insertMemberRole(new MemberRoleEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                    invitation.tenantId(), created.id(), invitation.roleId(), invitation.workspaceId(),
                    invitation.workspaceId() == null ? "TENANT" : "WORKSPACE:" + invitation.workspaceId(), null));
        }
        return new MemberData(
                created.id(), created.tenantId(), created.userId(), created.memberName(),
                created.memberStatus(), created.owner(), created.permissionVersion(),
                created.joinedAt(), created.lockVersion());
    }

    private void validateScope(long tenantId, long inviterMemberId, Long workspaceId, Long roleId) {
        if (workspaceId != null) {
            var workspace = workspaces.findById(tenantId, workspaceId).orElseThrow(ForbiddenException::new);
            if (!"ACTIVE".equals(workspace.workspaceStatus())
                    || !workspaces.isActiveMember(tenantId, workspaceId, inviterMemberId)) {
                throw new ForbiddenException();
            }
        }
        if (roleId != null) {
            actions.requireForMember(tenantId, inviterMemberId, null, "role:manage");
            actions.requireDelegable(tenantId, inviterMemberId, workspaceId, roleId);
        }
    }

    private InvitationData toData(InvitationEntity invitation, String rawToken) {
        return new InvitationData(
                invitation.id(), invitation.tenantId(), invitation.email(), invitation.workspaceId(),
                invitation.roleId(), invitation.invitationStatus(), invitation.expiresAt(), rawToken);
    }
}
