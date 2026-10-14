package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.entity.TenantEntity;
import com.aibi.platform.identity.dto.AddMemberRequest;
import com.aibi.platform.identity.dto.MemberData;
import com.aibi.platform.identity.dto.ChangeMemberStatusRequest;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.manager.TenantManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {

    private final MemberMapper memberMapper;
    private final UserMapper userMapper;
    private final TenantManager tenantManager;
    private final SessionService sessionService;
    private final PermissionContextService permissionContextService;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public MembershipService(MemberMapper memberMapper, UserMapper userMapper,
            TenantManager tenantManager, SessionService sessionService,
            PermissionContextService permissionContextService, TenantActionAuthorizer actions, SecurityAudit audit) {
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.tenantManager = tenantManager;
        this.sessionService = sessionService;
        this.permissionContextService = permissionContextService;
        this.actions = actions;
        this.audit = audit;
    }

    @MySqlTransactional(timeout = 5)
    public MemberData addMember(String authorization, long tenantId, AddMemberRequest request) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        TenantEntity tenant = tenantManager.getRequired(tenantId);
        if (!"ACTIVE".equals(tenant.tenantStatus())) {
            throw new ConflictException("租户未处于活动状态");
        }
        userMapper.findById(request.userId())
                .filter(user -> "ACTIVE".equals(user.userStatus()))
                .orElseThrow(() -> new NotFoundException("用户不存在或不可用"));
        Instant now = Instant.now();
        MemberEntity member = new MemberEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.userId(),
                request.memberName().trim(), "ACTIVE", false, 1, now, now, now, 0);
        var created = memberMapper.insert(member);
        audit.record(tenantId, actor.userId(), actor.memberId(), "MEMBER_ADDED", "MEMBER", created.id(), actor.tenantPermissionVersion());
        return toData(created);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<MemberData> myMemberships(String authorization) {
        TokenService.AccessClaims claims = sessionService.authenticate(authorization);
        return memberMapper.findByUserId(claims.userId()).stream().map(this::toData).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<com.aibi.platform.identity.dto.TenantSummaryData> myTenants(String authorization) {
        var claims = sessionService.authenticate(authorization);
        return memberMapper.findByUserId(claims.userId()).stream()
                .filter(member -> "ACTIVE".equals(member.memberStatus()))
                .map(member -> tenantManager.getRequired(member.tenantId()))
                .map(tenant -> new com.aibi.platform.identity.dto.TenantSummaryData(
                        tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.tenantStatus())).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<MemberData> members(String authorization, long tenantId) {
        permissionContextService.requireContext(authorization, tenantId);
        return memberMapper.findByTenantId(tenantId).stream().map(this::toData).toList();
    }

    public MemberEntity getRequired(long tenantId, long memberId) {
        return memberMapper.findById(tenantId, memberId)
                .orElseThrow(() -> new NotFoundException("成员不存在"));
    }

    public MemberEntity findActiveByUser(long tenantId, long userId) {
        return memberMapper.findByTenantAndUser(tenantId, userId)
                .filter(member -> "ACTIVE".equals(member.memberStatus()))
                .orElseThrow(() -> new NotFoundException("用户不是该租户的活动成员"));
    }

    @MySqlTransactional(timeout = 5)
    public MemberData disable(String authorization, long tenantId, long memberId,
            ChangeMemberStatusRequest request) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        MemberEntity current = getRequired(tenantId, memberId);
        if (current.owner()) {
            throw new ForbiddenException();
        }
        if (!"ACTIVE".equals(current.memberStatus())) {
            throw new ConflictException("成员当前状态不允许停用");
        }
        MemberEntity disabled = new MemberEntity(
                current.id(), current.tenantId(), current.userId(), current.memberName(),
                "DISABLED", current.owner(), current.permissionVersion() + 1,
                current.joinedAt(), current.createdAt(), Instant.now(), current.lockVersion() + 1);
        var saved = memberMapper.update(disabled, request.lockVersion());
        audit.record(tenantId, actor.userId(), actor.memberId(), "MEMBER_DISABLED", "MEMBER", memberId, actor.tenantPermissionVersion());
        return toData(saved);
    }

    @MySqlTransactional(timeout = 5)
    public void remove(String authorization, long tenantId, long memberId, long lockVersion) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        MemberEntity current = getRequired(tenantId, memberId);
        if (current.owner()) {
            throw new ForbiddenException();
        }
        if (current.lockVersion() != lockVersion || !memberMapper.softDelete(tenantId, memberId, lockVersion)) {
            throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        audit.record(tenantId, actor.userId(), actor.memberId(), "MEMBER_REMOVED", "MEMBER", memberId, actor.tenantPermissionVersion());
    }

    private MemberData toData(MemberEntity member) {
        return new MemberData(
                member.id(), member.tenantId(), member.userId(), member.memberName(),
                member.memberStatus(), member.owner(), member.permissionVersion(),
                member.joinedAt(), member.lockVersion());
    }
}
