package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.MemberEntity;
import java.util.List;
import java.util.Optional;

public interface MemberMapper {

    MemberEntity insert(MemberEntity member);

    Optional<MemberEntity> findById(long tenantId, long memberId);

    Optional<MemberEntity> findByTenantAndUser(long tenantId, long userId);

    default Optional<MemberEntity> findByTenantAndUserForUpdate(long tenantId, long userId) {
        return findByTenantAndUser(tenantId, userId);
    }

    List<MemberEntity> findByTenantId(long tenantId);

    List<MemberEntity> findByUserId(long userId);

    MemberEntity update(MemberEntity member, long expectedLockVersion);

    boolean softDelete(long tenantId, long memberId, long expectedLockVersion);
}
