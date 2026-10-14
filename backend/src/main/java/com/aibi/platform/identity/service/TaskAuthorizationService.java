package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.AccessDecisionData;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.security.TokenService;
import org.springframework.stereotype.Service;

@Service
public class TaskAuthorizationService {

    private final PermissionContextService permissionContextService;
    private final AccessDecisionService accessDecisionService;

    public TaskAuthorizationService(PermissionContextService permissionContextService,
            AccessDecisionService accessDecisionService) {
        this.permissionContextService = permissionContextService;
        this.accessDecisionService = accessDecisionService;
    }

    public TaskAuthorizationSnapshot capture(String authorization, long tenantId,
            AccessCheckRequest request) {
        TokenService.TenantClaims claims = permissionContextService.requireContext(
                authorization, tenantId);
        accessDecisionService.require(authorization, tenantId, request);
        return new TaskAuthorizationSnapshot(
                claims.sessionId(), claims.userId(), claims.tenantId(), claims.memberId(),
                claims.tenantPermissionVersion(), claims.memberPermissionVersion(), request);
    }

    public AccessDecisionData reauthorize(TaskAuthorizationSnapshot snapshot) {
        permissionContextService.requireCurrentContext(
                snapshot.sessionId(), snapshot.userId(), snapshot.tenantId(), snapshot.memberId(),
                snapshot.tenantPermissionVersion(), snapshot.memberPermissionVersion());
        AccessDecisionData decision = accessDecisionService.checkForMember(
                snapshot.tenantId(), snapshot.memberId(), snapshot.accessRequest());
        if (!decision.allowed()) {
            throw new ForbiddenException();
        }
        return decision;
    }
}
