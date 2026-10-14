package com.aibi.platform.service.impl;

import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.dto.ChangeTenantStatusRequest;
import com.aibi.platform.common.dto.TenantData;
import com.aibi.platform.common.dto.UpdateTenantRequest;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.entity.TenantEntity;
import com.aibi.platform.manager.TenantManager;
import com.aibi.platform.identity.service.SessionService;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.identity.service.TenantActionAuthorizer;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.entity.RolePermissionEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.service.TenantService;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl implements TenantService {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String FROZEN_STATUS = "FROZEN";

    private final TenantManager tenantManager;
    private final SessionService sessions;
    private final PermissionContextService contexts;
    private final TenantActionAuthorizer actions;
    private final MemberMapper members;
    private final UserMapper users;
    private final AccessControlMapper access;
    private final com.aibi.platform.identity.service.SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public TenantServiceImpl(TenantManager tenantManager, SessionService sessions,
            PermissionContextService contexts, TenantActionAuthorizer actions,
            MemberMapper members, UserMapper users, AccessControlMapper access,
            com.aibi.platform.identity.service.SecurityAudit audit) {
        this.tenantManager = tenantManager;
        this.sessions = sessions;
        this.contexts = contexts;
        this.actions = actions;
        this.members = members;
        this.users = users;
        this.access = access;
        this.audit = audit;
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public TenantData createTenant(String authorization, CreateTenantRequest request) {
        return createTenant(authorization, request, null);
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public TenantData createTenant(String authorization, CreateTenantRequest request, String idempotencyKey) {
        var identity = sessions.authenticate(authorization);
        var user = users.findById(identity.userId()).orElseThrow(
                com.aibi.platform.common.exception.UnauthorizedException::new);
        String timezone = defaultIfBlank(request.timezone(), DEFAULT_TIMEZONE);
        validateTimezone(timezone);
        String currency = defaultIfBlank(request.defaultCurrency(), DEFAULT_CURRENCY);
        String keyHash = "";
        String bodyHash = "";
        long newId = secureRandom.nextLong(1, Long.MAX_VALUE);
        if (idempotencyKey != null) {
            if (!idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
                throw new BadRequestException("Idempotency-Key 必须为8—128位安全字符");
            }
            keyHash = sessions.hashForIdempotency(identity.userId() + ":" + idempotencyKey);
            bodyHash = sessions.hashForIdempotency(new com.fasterxml.jackson.databind.ObjectMapper()
                    .valueToTree(java.util.List.of(request.tenantCode(), request.tenantName().trim(), timezone, currency)).toString());
            newId = java.nio.ByteBuffer.wrap(java.util.HexFormat.of().parseHex(keyHash)).getLong() & Long.MAX_VALUE;
            if (newId == 0) { newId = 1; }
            if (tenantManager.find(newId, false).isPresent()) {
                return replayOnboarding(newId, identity.userId(), keyHash, bodyHash);
            }
        }
        Instant now = Instant.now();
        TenantEntity created;
        try {
            created = tenantManager.create(new TenantEntity(
                newId,
                request.tenantCode(),
                request.tenantName().trim(),
                ACTIVE_STATUS,
                timezone,
                currency,
                1,
                now,
                now,
                0));
        } catch (org.springframework.dao.DuplicateKeyException
                | com.aibi.platform.common.exception.TenantCodeAlreadyExistsException conflict) {
            if (idempotencyKey != null) {
                return replayOnboarding(newId, identity.userId(), keyHash, bodyHash);
            }
            throw conflict;
        }
        // 企业及首任所有者由同一个MySQL事务写入，客户端不能指定owner身份。
        var owner = members.insert(new MemberEntity(secureRandom.nextLong(1, Long.MAX_VALUE), created.id(),
                identity.userId(), user.displayName(), "ACTIVE", true, 1, now, now, now, 0));
        var role = access.insertRole(new RoleEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                created.id(), "owner_admin", "企业所有者", "ACTIVE", true, 0));
        for (var permission : access.findPermissions()) {
            access.setRolePermission(new RolePermissionEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                    created.id(), role.id(), permission.id(), "ALLOW"));
        }
        access.insertMemberRole(new MemberRoleEntity(secureRandom.nextLong(1, Long.MAX_VALUE),
                created.id(), owner.id(), role.id(), null, "TENANT", null));
        audit.record(created.id(), identity.userId(), owner.id(), "TENANT_CREATED", "TENANT", created.id(), 1, keyHash, bodyHash);
        return toData(created);
    }

    private TenantData replayOnboarding(long tenantId, long userId, String keyHash, String bodyHash) {
        var existing = tenantManager.find(tenantId, true).orElseThrow(
                () -> new ConflictException("入驻请求冲突，请重试或更换租户编码"));
        var owner = members.findByTenantAndUserForUpdate(tenantId, userId)
                .filter(value -> value.owner() && "ACTIVE".equals(value.memberStatus()));
        if (owner.isEmpty() || !audit.matchesOnboarding(tenantId, userId, keyHash, bodyHash)) {
            throw new ConflictException("Idempotency-Key 已使用或请求内容不一致");
        }
        return toData(existing);
    }

    @Override
    @MySqlTransactional(readOnly = true, timeout = 5)
    public TenantData getTenant(String authorization, long tenantId) {
        contexts.requireContext(authorization, tenantId);
        return toData(tenantManager.getRequired(tenantId));
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public TenantData updateTenant(String authorization, long tenantId, UpdateTenantRequest request) {
        var actor = actions.require(authorization, tenantId, "tenant:manage");
        validateTimezone(request.timezone());
        TenantEntity current = tenantManager.getRequired(tenantId);
        TenantEntity updated = new TenantEntity(
                current.id(),
                current.tenantCode(),
                request.tenantName().trim(),
                current.tenantStatus(),
                request.timezone(),
                request.defaultCurrency(),
                current.permissionVersion(),
                current.createdAt(),
                Instant.now(),
                current.lockVersion() + 1);
        var saved = tenantManager.updateBasicInfo(updated, request.lockVersion());
        audit.record(tenantId, actor.userId(), actor.memberId(), "TENANT_UPDATED", "TENANT", tenantId, saved.permissionVersion());
        return toData(saved);
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public TenantData freezeTenant(String authorization, long tenantId, ChangeTenantStatusRequest request) {
        var actor = actions.require(authorization, tenantId, "tenant:manage");
        var result = changeStatus(tenantId, request.lockVersion(), ACTIVE_STATUS, FROZEN_STATUS);
        audit.record(tenantId, actor.userId(), actor.memberId(), "TENANT_FROZEN", "TENANT", tenantId, result.permissionVersion());
        return result;
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public TenantData unfreezeTenant(String authorization, long tenantId, ChangeTenantStatusRequest request) {
        contexts.requireRecoveryOwner(authorization, tenantId);
        var actor = sessions.authenticate(authorization);
        var owner = members.findByTenantAndUser(tenantId, actor.userId()).orElseThrow();
        var result = changeStatus(tenantId, request.lockVersion(), FROZEN_STATUS, ACTIVE_STATUS);
        audit.record(tenantId, actor.userId(), owner.id(), "TENANT_UNFROZEN", "TENANT", tenantId, result.permissionVersion());
        return result;
    }

    private TenantData changeStatus(long tenantId, long expectedLockVersion,
            String requiredStatus, String targetStatus) {
        TenantEntity current = tenantManager.getRequired(tenantId);
        if (!requiredStatus.equals(current.tenantStatus())) {
            throw new ConflictException("租户当前状态不允许执行此操作");
        }
        TenantEntity updated = new TenantEntity(
                current.id(), current.tenantCode(), current.tenantName(), targetStatus,
                current.timezone(), current.defaultCurrency(), current.permissionVersion() + 1,
                current.createdAt(), Instant.now(), current.lockVersion() + 1);
        return toData(tenantManager.updateStatus(updated, expectedLockVersion));
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new BadRequestException("时区标识不合法");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private TenantData toData(TenantEntity tenant) {
        return new TenantData(
                tenant.id(),
                tenant.tenantCode(),
                tenant.tenantName(),
                tenant.tenantStatus(),
                tenant.timezone(),
                tenant.defaultCurrency(),
                tenant.permissionVersion(),
                tenant.createdAt(),
                tenant.updatedAt(),
                tenant.lockVersion());
    }
}
