package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.AccessDecisionData;
import com.aibi.platform.identity.dto.AssetData;
import com.aibi.platform.identity.dto.CreateAssetRequest;
import com.aibi.platform.identity.dto.GrantAssetAclRequest;
import com.aibi.platform.identity.entity.AssetAclEntity;
import com.aibi.platform.identity.entity.AssetEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import com.aibi.platform.identity.mapper.DepartmentMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.MemberDepartmentMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.manager.TenantManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AccessDecisionService {

    private final AccessControlMapper accessControlMapper;
    private final AssetAccessMapper assetAccessMapper;
    private final MemberMapper memberMapper;
    private final MemberDepartmentMapper memberDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final MemberRoleService memberRoleService;
    private final WorkspaceService workspaceService;
    private final PermissionContextService permissionContextService;
    private final TenantManager tenantManager;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessDecisionService(AccessControlMapper accessControlMapper,
            AssetAccessMapper assetAccessMapper, MemberMapper memberMapper,
            MemberDepartmentMapper memberDepartmentMapper, DepartmentMapper departmentMapper,
            MemberRoleService memberRoleService,
            WorkspaceService workspaceService, PermissionContextService permissionContextService,
            TenantManager tenantManager, TenantActionAuthorizer actions, SecurityAudit audit) {
        this.accessControlMapper = accessControlMapper;
        this.assetAccessMapper = assetAccessMapper;
        this.memberMapper = memberMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.departmentMapper = departmentMapper;
        this.memberRoleService = memberRoleService;
        this.workspaceService = workspaceService;
        this.permissionContextService = permissionContextService;
        this.tenantManager = tenantManager;
        this.actions = actions;
        this.audit = audit;
    }

    @MySqlTransactional(timeout = 5)
    public AssetData createAsset(String authorization, long tenantId, CreateAssetRequest request) {
        TokenService.TenantClaims claims = actions.require(authorization, tenantId, "workspace:manage");
        if (Set.of("DATASOURCE", "FILE", "SYNC_JOB", "IMPORT_JOB").contains(request.assetType().toUpperCase(Locale.ROOT))) {
            throw new com.aibi.platform.common.exception.BadRequestException("该资产只能通过受控业务接口登记");
        }
        var workspace = workspaceService.required(tenantId, request.workspaceId());
        if (!"ACTIVE".equals(workspace.workspaceStatus())
                || !workspaceService.isActiveMember(tenantId, workspace.id(), claims.memberId())) {
            throw new ForbiddenException();
        }
        AssetEntity asset = assetAccessMapper.insertAsset(new AssetEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, workspace.id(),
                request.assetType().toUpperCase(Locale.ROOT), request.assetRefId(),
                claims.memberId(), "ACTIVE"));
        return toData(asset);
    }

    @MySqlTransactional(timeout = 5)
    public void setAcl(String authorization, long tenantId, long assetId, GrantAssetAclRequest request) {
        var actor = actions.require(authorization, tenantId, "policy:manage");
        requiredAsset(tenantId, assetId);
        validatePrincipal(tenantId, request.principalType(), request.principalId());
        assetAccessMapper.setAcl(new AssetAclEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, assetId,
                request.principalType(), request.principalId(), request.actionCode(),
                request.effect(), request.expiresAt()));
        var changed = tenantManager.incrementPermissionVersion(tenantId);
        audit.record(actor, "ASSET_ACL_CHANGED", "ASSET", assetId, changed.permissionVersion());
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public AccessDecisionData check(String authorization, long tenantId, AccessCheckRequest request) {
        TokenService.TenantClaims claims = permissionContextService.requireContext(authorization, tenantId);
        return checkForMember(tenantId, claims.memberId(), request);
    }

    public AccessDecisionData checkForMember(long tenantId, long memberId,
            AccessCheckRequest request) {
        Instant now = Instant.now();
        Long workspaceId = request.workspaceId();
        AssetEntity asset = null;
        if (request.assetId() != null) {
            asset = requiredAsset(tenantId, request.assetId());
            workspaceId = asset.workspaceId();
            if (request.workspaceId() != null && request.workspaceId() != asset.workspaceId()) {
                return new AccessDecisionData(false, "ASSET_WORKSPACE_MISMATCH");
            }
        }
        if (workspaceId != null) {
            var workspace = workspaceService.required(tenantId, workspaceId);
            if (!"ACTIVE".equals(workspace.workspaceStatus())
                    || !workspaceService.isActiveMember(tenantId, workspaceId, memberId)) {
                return new AccessDecisionData(false, "WORKSPACE_MEMBERSHIP_REQUIRED");
            }
        }
        PermissionEntity permission = accessControlMapper
                .findPermissionByCode(request.permissionCode()).orElse(null);
        if (permission == null && ResourceActionCatalog.legacyAlias(request.permissionCode()) != null) {
            permission = accessControlMapper.findPermissionByCode(ResourceActionCatalog.legacyAlias(request.permissionCode())).orElse(null);
        }
        if (permission == null) {
            return new AccessDecisionData(false, "PERMISSION_NOT_DEFINED");
        }
        List<MemberRoleEntity> assignments = memberRoleService.effectiveAssignments(
                tenantId, memberId, workspaceId, now);
        Set<Long> roleIds = assignments.stream().map(MemberRoleEntity::roleId).collect(Collectors.toSet());
        Set<Long> departmentIds = memberDepartmentMapper.findDepartmentIds(tenantId, memberId);
        boolean roleDeny = actions.explicitlyDenied(tenantId, memberId, workspaceId, request.permissionCode());
        if (roleDeny) {
            return new AccessDecisionData(false, "RBAC_EXPLICIT_DENY");
        }
        boolean roleAllow = actions.allowed(tenantId, memberId, workspaceId, request.permissionCode());
        if (asset == null) {
            return new AccessDecisionData(roleAllow, roleAllow ? "RBAC_ALLOW" : "RBAC_NO_ALLOW");
        }
        String action = request.actionCode() == null || request.actionCode().isBlank()
                ? permission.actionCode() : request.actionCode();
        var aclCandidates = new java.util.ArrayList<>(assetAccessMapper.findAcls(tenantId, asset.id(), action));
        if (ResourceActionCatalog.legacyAlias(request.permissionCode()) != null && !"manage".equals(action)) {
            aclCandidates.addAll(assetAccessMapper.findAcls(tenantId, asset.id(), "manage"));
        }
        List<AssetAclEntity> applicableAcls = aclCandidates.stream()
                .filter(value -> value.isEffectiveAt(now))
                .filter(value -> applies(value, memberId, roleIds, departmentIds)).toList();
        if (applicableAcls.stream().anyMatch(value -> "DENY".equals(value.effect()))) {
            return new AccessDecisionData(false, "ACL_EXPLICIT_DENY");
        }
        if (!roleAllow && Set.of("DATASOURCE", "FILE", "IMPORT_JOB", "SYNC_JOB").contains(asset.assetType())) {
            return new AccessDecisionData(false, "RBAC_NO_ALLOW");
        }
        if (applicableAcls.stream().anyMatch(value -> "ALLOW".equals(value.effect()))) {
            return new AccessDecisionData(true, "ACL_ALLOW");
        }
        if (asset.ownerMemberId() == memberId) {
            return new AccessDecisionData(true, "ASSET_OWNER");
        }
        return new AccessDecisionData(roleAllow, roleAllow ? "RBAC_ALLOW" : "NO_ALLOW");
    }

    public AccessDecisionData require(String authorization, long tenantId, AccessCheckRequest request) {
        AccessDecisionData decision = check(authorization, tenantId, request);
        if (!decision.allowed()) {
            throw new ForbiddenException();
        }
        return decision;
    }

    private boolean applies(AssetAclEntity acl, long memberId, Set<Long> roleIds,
            Set<Long> departmentIds) {
        return switch (acl.principalType()) {
            case "MEMBER" -> acl.principalId() == memberId;
            case "ROLE" -> roleIds.contains(acl.principalId());
            case "DEPARTMENT" -> departmentIds.contains(acl.principalId());
            default -> false;
        };
    }

    private void validatePrincipal(long tenantId, String type, long principalId) {
        boolean exists = switch (type) {
            case "MEMBER" -> memberMapper.findById(tenantId, principalId).isPresent();
            case "ROLE" -> accessControlMapper.findRole(tenantId, principalId).isPresent();
            case "DEPARTMENT" -> departmentMapper.findById(tenantId, principalId).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new NotFoundException("ACL 主体不存在");
        }
    }

    private AssetEntity requiredAsset(long tenantId, long assetId) {
        AssetEntity asset = assetAccessMapper.findAsset(tenantId, assetId)
                .orElseThrow(() -> new NotFoundException("资产不存在"));
        if (!"ACTIVE".equals(asset.assetStatus())) {
            throw new ConflictException("资产未处于活动状态");
        }
        return asset;
    }

    private AssetData toData(AssetEntity asset) {
        return new AssetData(
                asset.id(), asset.tenantId(), asset.workspaceId(), asset.assetType(),
                asset.assetRefId(), asset.ownerMemberId(), asset.assetStatus());
    }
}
