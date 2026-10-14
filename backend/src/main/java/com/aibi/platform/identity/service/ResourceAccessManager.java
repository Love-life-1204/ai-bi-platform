package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.entity.AssetEntity;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import com.aibi.platform.identity.mapper.WorkspaceMapper;
import com.aibi.platform.identity.security.TokenService;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 角色能力、工作空间成员和真实资产范围取交集；资源owner不能跳过角色能力。 */
@Component
public class ResourceAccessManager {

    private final PermissionContextService contexts;
    private final WorkspaceMapper workspaces;
    private final TenantActionAuthorizer actions;
    private final AssetAccessMapper assets;
    private final AccessDecisionService decisions;
    private final SecureRandom random = new SecureRandom();

    public ResourceAccessManager(PermissionContextService contexts, WorkspaceMapper workspaces,
            TenantActionAuthorizer actions, AssetAccessMapper assets, AccessDecisionService decisions) {
        this.contexts = contexts;
        this.workspaces = workspaces;
        this.actions = actions;
        this.assets = assets;
        this.decisions = decisions;
    }

    public TokenService.TenantClaims requireTenant(String authorization, long tenantId) {
        return contexts.requireContext(authorization, tenantId);
    }

    public TokenService.TenantClaims requireWorkspace(String authorization, long tenantId, long workspaceId, String permission) {
        var claims = requireTenant(authorization, tenantId);
        requireWorkspaceMember(tenantId, claims.memberId(), workspaceId);
        actions.requireForMember(tenantId, claims.memberId(), workspaceId, permission);
        return claims;
    }

    public long resolveWorkspace(String authorization, long tenantId, Long requested, String permission) {
        if (requested != null) {
            requireWorkspace(authorization, tenantId, requested, permission);
            return requested;
        }
        var actor = requireTenant(authorization, tenantId);
        var available = workspaces.findByTenantId(tenantId).stream()
                .filter(w -> "ACTIVE".equals(w.workspaceStatus()) && workspaces.isActiveMember(tenantId, w.id(), actor.memberId()))
                .filter(w -> actions.allowed(tenantId, actor.memberId(), w.id(), permission)).toList();
        if (available.isEmpty()) {
            throw new ForbiddenException();
        }
        if (available.size() != 1) {
            throw new BadRequestException("请明确选择工作空间");
        }
        return available.get(0).id();
    }

    public void require(String authorization, long tenantId, long workspaceId, String type, long id, String permission) {
        var actor = requireTenant(authorization, tenantId);
        if (!can(actor, workspaceId, type, id, permission)) {
            throw new NotFoundException("资源不存在或不可访问");
        }
    }

    public void requireAnyWorkspacePermission(String authorization, long tenantId, String permission) {
        var actor = requireTenant(authorization, tenantId);
        boolean allowed = workspaces.findByTenantId(tenantId).stream()
                .anyMatch(workspace -> "ACTIVE".equals(workspace.workspaceStatus())
                        && workspaces.isActiveMember(tenantId, workspace.id(), actor.memberId())
                        && actions.allowed(tenantId, actor.memberId(), workspace.id(), permission));
        if (!allowed) {
            throw new ForbiddenException();
        }
    }

    public AssetEntity requireReference(String authorization, long tenantId, String type, long id, String permission) {
        requireAnyWorkspacePermission(authorization, tenantId, permission);
        var asset = assets.findAssetByReference(tenantId, type, id)
                .orElseThrow(() -> new NotFoundException("资源不存在或不可访问"));
        require(authorization, tenantId, asset.workspaceId(), type, id, permission);
        return asset;
    }

    public boolean canReference(TokenService.TenantClaims actor, String type, long id, String permission) {
        var asset = assets.findAssetByReference(actor.tenantId(), type, id).orElse(null);
        return asset != null && can(actor, asset.workspaceId(), type, id, permission);
    }

    public boolean can(TokenService.TenantClaims actor, long workspaceId, String type, long id, String permission) {
        var workspace = workspaces.findById(actor.tenantId(), workspaceId).orElse(null);
        if (workspace == null || !"ACTIVE".equals(workspace.workspaceStatus())
                || !workspaces.isActiveMember(actor.tenantId(), workspaceId, actor.memberId())
                || !actions.allowed(actor.tenantId(), actor.memberId(), workspaceId, permission)) {
            return false;
        }
        var asset = assets.findAssetByReference(actor.tenantId(), type, id).orElse(null);
        return asset != null && "ACTIVE".equals(asset.assetStatus()) && asset.workspaceId() == workspaceId
                && decisions.checkForMember(actor.tenantId(), actor.memberId(), new AccessCheckRequest(
                        workspaceId, permission, asset.id(), permission.substring(permission.indexOf(':') + 1))).allowed();
    }

    public AssetEntity register(long tenantId, long workspaceId, long ownerMemberId, String type, long id) {
        var existing = assets.findAssetByReference(tenantId, type, id).orElse(null);
        if (existing != null) {
            if (existing.workspaceId() != workspaceId || existing.ownerMemberId() != ownerMemberId) {
                throw new ForbiddenException();
            }
            return existing;
        }
        requireWorkspaceMember(tenantId, ownerMemberId, workspaceId);
        return assets.insertAsset(new AssetEntity(random.nextLong(1, Long.MAX_VALUE), tenantId, workspaceId,
                type, id, ownerMemberId, "ACTIVE"));
    }

    public void requireWorkspaceMember(long tenantId, long memberId, long workspaceId) {
        var workspace = workspaces.findById(tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("工作空间不存在"));
        if (!"ACTIVE".equals(workspace.workspaceStatus()) || !workspaces.isActiveMember(tenantId, workspaceId, memberId)) {
            throw new ForbiddenException();
        }
    }
}
