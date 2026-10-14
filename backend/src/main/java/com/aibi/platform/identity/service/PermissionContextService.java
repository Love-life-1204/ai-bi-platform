package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.entity.TenantEntity;
import com.aibi.platform.identity.dto.SwitchTenantRequest;
import com.aibi.platform.identity.dto.TenantContextData;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.manager.TenantManager;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PermissionContextService {

    private final SessionService sessionService;
    private final TenantManager tenantManager;
    private final MemberMapper memberMapper;
    private final TokenService tokenService;

    public PermissionContextService(SessionService sessionService, TenantManager tenantManager,
            MemberMapper memberMapper, TokenService tokenService) {
        this.sessionService = sessionService;
        this.tenantManager = tenantManager;
        this.memberMapper = memberMapper;
        this.tokenService = tokenService;
    }

    public TenantContextData switchTenant(String authorization, SwitchTenantRequest request) {
        TokenService.AccessClaims access = sessionService.authenticate(authorization);
        TenantEntity tenant = tenantManager.getRequired(request.tenantId());
        if (!"ACTIVE".equals(tenant.tenantStatus())) {
            throw new ForbiddenException();
        }
        MemberEntity member = memberMapper.findByTenantAndUser(tenant.id(), access.userId())
                .filter(value -> "ACTIVE".equals(value.memberStatus()))
                .orElseThrow(ForbiddenException::new);
        TokenService.IssuedTenantToken issued = tokenService.issueTenantToken(
                access, tenant.id(), member.id(), tenant.permissionVersion(),
                member.permissionVersion(), Instant.now());
        return new TenantContextData("Bearer", issued.value(), issued.expiresAt(),
                tenant.id(), member.id(), tenant.permissionVersion(), member.permissionVersion());
    }

    public TokenService.TenantClaims requireContext(String authorization, long expectedTenantId) {
        TokenService.TenantClaims claims = tokenService.verifyTenantToken(
                sessionService.bearerToken(authorization), Instant.now());
        if (claims.tenantId() != expectedTenantId) {
            throw new ForbiddenException();
        }
        requireCurrentContext(
                claims.sessionId(), claims.userId(), claims.tenantId(), claims.memberId(),
                claims.tenantPermissionVersion(), claims.memberPermissionVersion());
        return claims;
    }

    public TokenService.TenantClaims requireAuthenticatedContext(String authorization) {
        TokenService.TenantClaims claims = tokenService.verifyTenantToken(
                sessionService.bearerToken(authorization), Instant.now());
        return requireContext(authorization, claims.tenantId());
    }

    /** 冻结租户仅在解冻操作中允许全局会话+有效owner恢复，不放宽通用租户校验。 */
    public void requireRecoveryOwner(String authorization, long tenantId) {
        TokenService.AccessClaims access = sessionService.authenticate(authorization);
        memberMapper.findByTenantAndUser(tenantId, access.userId())
                .filter(member -> "ACTIVE".equals(member.memberStatus()) && member.owner())
                .orElseThrow(ForbiddenException::new);
    }

    public void requireCurrentContext(long sessionId, long userId, long tenantId, long memberId,
            long tenantPermissionVersion, long memberPermissionVersion) {
        sessionService.requireActiveSession(sessionId, userId);
        TenantEntity tenant = tenantManager.getRequired(tenantId);
        MemberEntity member = memberMapper.findById(tenantId, memberId)
                .filter(value -> value.userId() == userId)
                .orElseThrow(ForbiddenException::new);
        if (!"ACTIVE".equals(tenant.tenantStatus()) || !"ACTIVE".equals(member.memberStatus())
                || tenant.permissionVersion() != tenantPermissionVersion
                || member.permissionVersion() != memberPermissionVersion) {
            throw new ForbiddenException();
        }
    }
}
