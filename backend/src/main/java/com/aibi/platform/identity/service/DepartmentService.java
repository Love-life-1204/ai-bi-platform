package com.aibi.platform.identity.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.CreateDepartmentRequest;
import com.aibi.platform.identity.dto.DepartmentData;
import com.aibi.platform.identity.dto.AssignDepartmentMemberRequest;
import com.aibi.platform.identity.dto.UpdateDepartmentRequest;
import com.aibi.platform.identity.entity.DepartmentEntity;
import com.aibi.platform.identity.entity.MemberDepartmentEntity;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.DepartmentMapper;
import com.aibi.platform.identity.mapper.MemberDepartmentMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final PermissionContextService permissionContextService;
    private final MemberMapper memberMapper;
    private final MemberDepartmentMapper memberDepartmentMapper;
    private final TenantActionAuthorizer actions;
    private final SecurityAudit audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public DepartmentService(DepartmentMapper departmentMapper,
            PermissionContextService permissionContextService, MemberMapper memberMapper,
            MemberDepartmentMapper memberDepartmentMapper, TenantActionAuthorizer actions, SecurityAudit audit) {
        this.departmentMapper = departmentMapper;
        this.permissionContextService = permissionContextService;
        this.memberMapper = memberMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.actions = actions;
        this.audit = audit;
    }

    @MySqlTransactional(timeout = 5)
    public DepartmentData create(String authorization, long tenantId, CreateDepartmentRequest request) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        validateParent(tenantId, request.parentId(), null);
        Instant now = Instant.now();
        DepartmentEntity department = new DepartmentEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, request.parentId(),
                request.departmentCode(), request.departmentName().trim(), request.sortOrder(),
                now, now, 0);
        var result = toData(departmentMapper.insert(department), List.of());
        audit.record(actor, "DEPARTMENT_CREATED", "DEPARTMENT", department.id(), actor.tenantPermissionVersion());
        return result;
    }

    @MySqlTransactional(timeout = 5)
    public DepartmentData update(String authorization, long tenantId, long departmentId,
            UpdateDepartmentRequest request) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        DepartmentEntity current = required(tenantId, departmentId);
        validateParent(tenantId, request.parentId(), departmentId);
        DepartmentEntity updated = new DepartmentEntity(
                current.id(), current.tenantId(), request.parentId(), current.departmentCode(),
                request.departmentName().trim(), request.sortOrder(), current.createdAt(),
                Instant.now(), current.lockVersion() + 1);
        var result = toData(departmentMapper.update(updated, request.lockVersion()), List.of());
        audit.record(actor, "DEPARTMENT_UPDATED", "DEPARTMENT", departmentId, actor.tenantPermissionVersion());
        return result;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<DepartmentData> tree(String authorization, long tenantId) {
        permissionContextService.requireContext(authorization, tenantId);
        List<DepartmentEntity> departments = departmentMapper.findByTenantId(tenantId);
        Map<Long, List<DepartmentEntity>> children = new HashMap<>();
        for (DepartmentEntity department : departments) {
            long parentKey = department.parentId() == null ? 0 : department.parentId();
            children.computeIfAbsent(parentKey, ignored -> new java.util.ArrayList<>()).add(department);
        }
        return children.getOrDefault(0L, List.of()).stream()
                .map(value -> buildTree(value, children)).toList();
    }

    @MySqlTransactional(timeout = 5)
    public void addMember(String authorization, long tenantId, long departmentId,
            AssignDepartmentMemberRequest request) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        required(tenantId, departmentId);
        MemberEntity member = requiredMember(tenantId, request.memberId());
        if (!"ACTIVE".equals(member.memberStatus())) {
            throw new ConflictException("成员未处于活动状态");
        }
        memberDepartmentMapper.insert(new MemberDepartmentEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), tenantId, member.id(),
                departmentId, request.primary()));
        bumpMember(member, request.memberLockVersion());
        audit.record(actor, "DEPARTMENT_MEMBER_GRANTED", "MEMBER", member.id(), member.permissionVersion() + 1);
    }

    @MySqlTransactional(timeout = 5)
    public void removeMember(String authorization, long tenantId, long departmentId,
            long memberId, long memberLockVersion) {
        var actor = actions.require(authorization, tenantId, "member:manage");
        required(tenantId, departmentId);
        MemberEntity member = requiredMember(tenantId, memberId);
        if (!memberDepartmentMapper.remove(tenantId, departmentId, memberId)) {
            throw new NotFoundException("成员部门关系不存在");
        }
        bumpMember(member, memberLockVersion);
        audit.record(actor, "DEPARTMENT_MEMBER_REVOKED", "MEMBER", member.id(), member.permissionVersion() + 1);
    }

    private DepartmentData buildTree(DepartmentEntity department,
            Map<Long, List<DepartmentEntity>> children) {
        List<DepartmentData> nodes = children.getOrDefault(department.id(), List.of()).stream()
                .map(value -> buildTree(value, children)).toList();
        return toData(department, nodes);
    }

    private void validateParent(long tenantId, Long parentId, Long currentDepartmentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentDepartmentId)) {
            throw new ConflictException("部门不能作为自己的上级");
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (!visited.add(cursor) || cursor.equals(currentDepartmentId)) {
                throw new ConflictException("部门层级不能形成循环");
            }
            cursor = required(tenantId, cursor).parentId();
        }
    }

    private DepartmentEntity required(long tenantId, long departmentId) {
        return departmentMapper.findById(tenantId, departmentId)
                .orElseThrow(() -> new NotFoundException("部门不存在"));
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

    private DepartmentData toData(DepartmentEntity department, List<DepartmentData> children) {
        return new DepartmentData(
                department.id(), department.tenantId(), department.parentId(),
                department.departmentCode(), department.departmentName(), department.sortOrder(),
                department.lockVersion(), children);
    }
}
