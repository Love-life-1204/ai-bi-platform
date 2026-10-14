package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.entity.WorkspaceMemberEntity;
import java.util.List;
import java.util.Optional;

public interface WorkspaceMapper {

    WorkspaceEntity insert(WorkspaceEntity workspace);

    Optional<WorkspaceEntity> findById(long tenantId, long workspaceId);

    List<WorkspaceEntity> findByTenantId(long tenantId);

    WorkspaceEntity update(WorkspaceEntity workspace, long expectedLockVersion);

    WorkspaceMemberEntity addMember(WorkspaceMemberEntity member);

    boolean removeMember(long tenantId, long workspaceId, long memberId);

    boolean isActiveMember(long tenantId, long workspaceId, long memberId);
}
