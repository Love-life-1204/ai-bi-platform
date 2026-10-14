package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.MemberDepartmentEntity;
import java.util.Set;

public interface MemberDepartmentMapper {

    MemberDepartmentEntity insert(MemberDepartmentEntity value);

    boolean remove(long tenantId, long departmentId, long memberId);

    Set<Long> findDepartmentIds(long tenantId, long memberId);
}
