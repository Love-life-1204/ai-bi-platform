package com.aibi.platform.semantic.manager;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.AccessDecisionService;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.identity.service.WorkspaceService;
import org.springframework.stereotype.Component;

@Component
public class SemanticAccessManager {

    private final PermissionContextService permissionContextService;
    private final WorkspaceService workspaceService;
    private final AccessDecisionService accessDecisionService;

    public SemanticAccessManager(PermissionContextService permissionContextService,
            WorkspaceService workspaceService, AccessDecisionService accessDecisionService) {
        this.permissionContextService = permissionContextService;
        this.workspaceService = workspaceService;
        this.accessDecisionService = accessDecisionService;
    }

    public TokenService.TenantClaims requireWorkspace(String authorization, long tenantId, long workspaceId) {
        TokenService.TenantClaims claims = requireTenant(authorization, tenantId);
        WorkspaceEntity workspace = workspaceService.required(tenantId, workspaceId);
        if (!"ACTIVE".equals(workspace.workspaceStatus())
                || !workspaceService.isActiveMember(tenantId, workspaceId, claims.memberId())) {
            throw new ForbiddenException();
        }
        return claims;
    }

    public TokenService.TenantClaims requireTenant(String authorization, long tenantId) {
        return permissionContextService.requireContext(authorization, tenantId);
    }

    public boolean canAccessWorkspace(long tenantId, long workspaceId, long memberId) {
        WorkspaceEntity workspace = workspaceService.required(tenantId, workspaceId);
        return "ACTIVE".equals(workspace.workspaceStatus())
                && workspaceService.isActiveMember(tenantId, workspaceId, memberId);
    }

    public void requirePermission(String authorization, long tenantId, long workspaceId,
            String permissionCode, String actionCode) {
        accessDecisionService.require(authorization, tenantId,
                new AccessCheckRequest(workspaceId, permissionCode, null, actionCode));
    }

    public boolean hasPermission(String authorization, long tenantId, long workspaceId,
            String permissionCode, String actionCode) {
        try {
            requirePermission(authorization, tenantId, workspaceId, permissionCode, actionCode);
            return true;
        } catch (ForbiddenException exception) {
            return false;
        }
    }
}
