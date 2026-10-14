package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.entity.WorkspaceMemberEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryWorkspaceMapper implements WorkspaceMapper {

    private final ConcurrentMap<Long, WorkspaceEntity> workspaces = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> workspaceIdsByCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkspaceMemberEntity> members = new ConcurrentHashMap<>();

    @Override
    public WorkspaceEntity insert(WorkspaceEntity workspace) {
        String key = workspace.tenantId() + ":" + workspace.workspaceCode();
        if (workspaceIdsByCode.putIfAbsent(key, workspace.id()) != null
                || workspaces.putIfAbsent(workspace.id(), workspace) != null) {
            workspaceIdsByCode.remove(key, workspace.id());
            throw new ConflictException("工作空间编码已存在");
        }
        return workspace;
    }

    @Override
    public Optional<WorkspaceEntity> findById(long tenantId, long workspaceId) {
        return Optional.ofNullable(workspaces.get(workspaceId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<WorkspaceEntity> findByTenantId(long tenantId) {
        return workspaces.values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(WorkspaceEntity::workspaceCode)).toList();
    }

    @Override
    public WorkspaceEntity update(WorkspaceEntity workspace, long expectedLockVersion) {
        return workspaces.compute(workspace.id(), (id, current) -> {
            if (current == null || current.tenantId() != workspace.tenantId()) {
                throw new NotFoundException("工作空间不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return workspace;
        });
    }

    @Override
    public WorkspaceMemberEntity addMember(WorkspaceMemberEntity member) {
        if (members.putIfAbsent(memberKey(
                member.tenantId(), member.workspaceId(), member.memberId()), member) != null) {
            throw new ConflictException("成员已获得工作空间授权");
        }
        return member;
    }

    @Override
    public boolean removeMember(long tenantId, long workspaceId, long memberId) {
        return members.remove(memberKey(tenantId, workspaceId, memberId)) != null;
    }

    @Override
    public boolean isActiveMember(long tenantId, long workspaceId, long memberId) {
        WorkspaceMemberEntity member = members.get(memberKey(tenantId, workspaceId, memberId));
        return member != null && "ACTIVE".equals(member.memberStatus());
    }

    private String memberKey(long tenantId, long workspaceId, long memberId) {
        return tenantId + ":" + workspaceId + ":" + memberId;
    }
}
