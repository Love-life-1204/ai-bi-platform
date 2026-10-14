package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.entity.MemberEntity;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryMemberMapper implements MemberMapper {

    private final ConcurrentMap<Long, MemberEntity> members = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByTenantUser = new ConcurrentHashMap<>();

    @Override
    public MemberEntity insert(MemberEntity member) {
        String key = tenantUserKey(member.tenantId(), member.userId());
        if (idsByTenantUser.putIfAbsent(key, member.id()) != null
                || members.putIfAbsent(member.id(), member) != null) {
            idsByTenantUser.remove(key, member.id());
            throw new ConflictException("用户已是该租户成员");
        }
        return member;
    }

    @Override
    public Optional<MemberEntity> findById(long tenantId, long memberId) {
        return Optional.ofNullable(members.get(memberId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public Optional<MemberEntity> findByTenantAndUser(long tenantId, long userId) {
        Long id = idsByTenantUser.get(tenantUserKey(tenantId, userId));
        return id == null ? Optional.empty() : findById(tenantId, id);
    }

    @Override
    public List<MemberEntity> findByTenantId(long tenantId) {
        return members.values().stream()
                .filter(value -> value.tenantId() == tenantId)
                .sorted(java.util.Comparator.comparingLong(MemberEntity::id))
                .toList();
    }

    @Override
    public List<MemberEntity> findByUserId(long userId) {
        return members.values().stream()
                .filter(value -> value.userId() == userId)
                .sorted(java.util.Comparator.comparingLong(MemberEntity::tenantId))
                .toList();
    }

    @Override
    public MemberEntity update(MemberEntity member, long expectedLockVersion) {
        return members.compute(member.id(), (id, current) -> {
            if (current == null || current.tenantId() != member.tenantId()) {
                throw new NotFoundException("成员不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return member;
        });
    }

    @Override
    public boolean softDelete(long tenantId, long memberId, long expectedLockVersion) {
        MemberEntity current = findById(tenantId, memberId).orElse(null);
        if (current == null || current.lockVersion() != expectedLockVersion) {
            return false;
        }
        if (!members.remove(memberId, current)) {
            return false;
        }
        idsByTenantUser.remove(tenantUserKey(tenantId, current.userId()), memberId);
        return true;
    }

    private String tenantUserKey(long tenantId, long userId) {
        return tenantId + ":" + userId;
    }
}
