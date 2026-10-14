package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.ColumnAccessData;
import com.aibi.platform.identity.dto.CreateRowPolicyRequest;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationRequest;
import com.aibi.platform.identity.dto.GrantPolicyRequest;
import com.aibi.platform.identity.dto.SetColumnPolicyRequest;
import com.aibi.platform.identity.entity.ColumnPolicyEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import com.aibi.platform.identity.entity.PolicyGrantEntity;
import com.aibi.platform.identity.entity.RowPolicyEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.DataPolicyMapper;
import com.aibi.platform.identity.mapper.DepartmentMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.MemberDepartmentMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.manager.TenantManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DataPolicyService {

    private static final Map<String, Integer> COLUMN_MODE_PRIORITY = Map.of(
            "VISIBLE", 0, "MASKED", 1, "DENY", 2);

    private final DataPolicyMapper dataPolicyMapper;
    private final AccessControlMapper accessControlMapper;
    private final MemberMapper memberMapper;
    private final MemberDepartmentMapper memberDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final MemberRoleService memberRoleService;
    private final PermissionContextService permissionContextService;
    private final AccessDecisionService accessDecisionService;
    private final ObjectMapper objectMapper;
    private final TenantManager tenantManager;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public DataPolicyService(DataPolicyMapper dataPolicyMapper,
            AccessControlMapper accessControlMapper, MemberMapper memberMapper,
            MemberDepartmentMapper memberDepartmentMapper, DepartmentMapper departmentMapper,
            MemberRoleService memberRoleService,
            PermissionContextService permissionContextService,
            AccessDecisionService accessDecisionService, ObjectMapper objectMapper,
            TenantManager tenantManager, TenantActionAuthorizer actions, SecurityAudit audit) {
        this.dataPolicyMapper = dataPolicyMapper;
        this.accessControlMapper = accessControlMapper;
        this.memberMapper = memberMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.departmentMapper = departmentMapper;
        this.memberRoleService = memberRoleService;
        this.permissionContextService = permissionContextService;
        this.accessDecisionService = accessDecisionService;
        this.objectMapper = objectMapper;
        this.tenantManager = tenantManager;
        this.actions = actions;
        this.audit = audit;
    }

    @MySqlTransactional(timeout = 5)
    public RowPolicyEntity createRowPolicy(String authorization, long tenantId,
            CreateRowPolicyRequest request) {
        var actor = actions.require(authorization, tenantId, "policy:manage");
        RowPolicyEntity policy = new RowPolicyEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.datasetId(),
                request.policyCode(), request.policyName().trim(), request.policyAst().toString(),
                request.policyGroup().trim(), "ACTIVE", 1);
        var inserted = dataPolicyMapper.insertRowPolicy(policy);
        audit.record(actor, "ROW_POLICY_CREATED", "ROW_POLICY", policy.id(), actor.tenantPermissionVersion());
        return inserted;
    }

    @MySqlTransactional(timeout = 5)
    public PolicyGrantEntity grant(String authorization, long tenantId, long policyId,
            GrantPolicyRequest request) {
        var actor = actions.require(authorization, tenantId, "policy:manage");
        requiredPolicy(tenantId, policyId);
        validatePrincipal(tenantId, request.principalType(), request.principalId());
        PolicyGrantEntity grant = dataPolicyMapper.insertGrant(new PolicyGrantEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, policyId,
                request.principalType(), request.principalId(), request.expiresAt()));
        var changed = tenantManager.incrementPermissionVersion(tenantId);
        audit.record(actor, "ROW_POLICY_GRANTED", "ROW_POLICY", policyId, changed.permissionVersion());
        return grant;
    }

    @MySqlTransactional(timeout = 5)
    public ColumnPolicyEntity setColumnPolicy(String authorization, long tenantId,
            SetColumnPolicyRequest request) {
        var actor = actions.require(authorization, tenantId, "policy:manage");
        validatePrincipal(tenantId, request.principalType(), request.principalId());
        String maskConfig = request.maskConfig() == null ? null : request.maskConfig().toString();
        ColumnPolicyEntity policy = dataPolicyMapper.setColumnPolicy(new ColumnPolicyEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.datasetFieldId(),
                request.principalType(), request.principalId(), request.accessMode(), maskConfig));
        var changed = tenantManager.incrementPermissionVersion(tenantId);
        audit.record(actor, "COLUMN_POLICY_CHANGED", "DATASET_FIELD", request.datasetFieldId(), changed.permissionVersion());
        return policy;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public DataPolicyEvaluationData evaluate(String authorization, long tenantId,
            DataPolicyEvaluationRequest request) {
        TokenService.TenantClaims claims = permissionContextService.requireContext(
                authorization, tenantId);
        accessDecisionService.require(authorization, tenantId,
                new AccessCheckRequest(request.workspaceId(), "dataset:read", null, "read"));
        return evaluateForMember(tenantId, claims.memberId(), request);
    }

    /** Internal delegated-query entry point; caller must reauthorize the captured task first. */
    public DataPolicyEvaluationData evaluateForMember(long tenantId, long memberId,
            DataPolicyEvaluationRequest request) {
        Instant now = Instant.now();
        Set<Long> roleIds = memberRoleService.effectiveAssignments(
                        tenantId, memberId, request.workspaceId(), now).stream()
                .map(MemberRoleEntity::roleId).collect(Collectors.toSet());
        Set<Long> departmentIds = memberDepartmentMapper.findDepartmentIds(
                tenantId, memberId);
        Map<String, List<JsonNode>> rowPolicyGroups = effectiveRowPolicyGroups(
                tenantId, request.datasetId(), memberId, roleIds, departmentIds, now);
        List<ColumnAccessData> columns = effectiveColumnAccess(
                tenantId, request.datasetFieldIds(), memberId, roleIds, departmentIds);
        return new DataPolicyEvaluationData(request.datasetId(), rowPolicyGroups, columns);
    }

    private Map<String, List<JsonNode>> effectiveRowPolicyGroups(long tenantId, long datasetId,
            long memberId, Set<Long> roleIds, Set<Long> departmentIds, Instant now) {
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        Set<String> configuredGroups = new java.util.LinkedHashSet<>();
        for (RowPolicyEntity policy : dataPolicyMapper.findRowPolicies(tenantId, datasetId)) {
            if (!"ACTIVE".equals(policy.policyStatus())) {
                continue;
            }
            configuredGroups.add(policy.policyGroup());
            boolean granted = dataPolicyMapper.findGrants(tenantId, policy.id()).stream()
                    .filter(value -> value.isEffectiveAt(now))
                    .anyMatch(value -> applies(value.principalType(), value.principalId(),
                            memberId, roleIds, departmentIds));
            if (granted) {
                groups.computeIfAbsent(policy.policyGroup(), ignored -> new ArrayList<>())
                        .add(parseJson(policy.policyAst()));
            }
        }
        for (String configuredGroup : configuredGroups) {
            groups.computeIfAbsent(configuredGroup,
                    ignored -> List.of(objectMapper.createObjectNode().put("op", "false")));
        }
        return groups;
    }

    private List<ColumnAccessData> effectiveColumnAccess(long tenantId, List<Long> fieldIds,
            long memberId, Set<Long> roleIds, Set<Long> departmentIds) {
        List<ColumnPolicyEntity> configuredPolicies = dataPolicyMapper
                .findColumnPolicies(tenantId, fieldIds);
        Set<Long> configuredFieldIds = configuredPolicies.stream()
                .map(ColumnPolicyEntity::datasetFieldId).collect(Collectors.toSet());
        Map<Long, List<ColumnPolicyEntity>> byField = configuredPolicies.stream()
                .filter(value -> applies(value.principalType(), value.principalId(),
                        memberId, roleIds, departmentIds))
                .collect(Collectors.groupingBy(ColumnPolicyEntity::datasetFieldId));
        return fieldIds.stream().distinct().map(fieldId -> {
            ColumnPolicyEntity selected = byField.getOrDefault(fieldId, List.of()).stream()
                    .max((left, right) -> Integer.compare(
                            COLUMN_MODE_PRIORITY.get(left.accessMode()),
                            COLUMN_MODE_PRIORITY.get(right.accessMode())))
                    .orElse(null);
            if (selected == null) {
                String defaultMode = configuredFieldIds.contains(fieldId) ? "DENY" : "VISIBLE";
                return new ColumnAccessData(fieldId, defaultMode, null);
            }
            JsonNode maskConfig = selected.maskConfig() == null
                    ? null : parseJson(selected.maskConfig());
            return new ColumnAccessData(fieldId, selected.accessMode(), maskConfig);
        }).toList();
    }

    private boolean applies(String principalType, long principalId,
            long memberId, Set<Long> roleIds, Set<Long> departmentIds) {
        return switch (principalType) {
            case "MEMBER" -> principalId == memberId;
            case "ROLE" -> roleIds.contains(principalId);
            case "DEPARTMENT" -> departmentIds.contains(principalId);
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
            throw new NotFoundException("数据策略主体不存在");
        }
    }

    private RowPolicyEntity requiredPolicy(long tenantId, long policyId) {
        return dataPolicyMapper.findRowPolicy(tenantId, policyId)
                .orElseThrow(() -> new NotFoundException("行级策略不存在"));
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久化的数据策略 JSON 无效", exception);
        }
    }
}
