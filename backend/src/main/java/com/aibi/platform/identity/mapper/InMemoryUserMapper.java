package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.identity.entity.UserEntity;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryUserMapper implements UserMapper {

    private final ConcurrentMap<String, UserEntity> usersByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, UserEntity> usersById = new ConcurrentHashMap<>();

    @Override
    public Optional<UserEntity> findByEmail(String normalizedEmail) {
        return Optional.ofNullable(usersByEmail.get(normalizedEmail));
    }

    @Override
    public Optional<UserEntity> findById(long userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public UserEntity insert(UserEntity user) {
        if (usersByEmail.putIfAbsent(user.email(), user) != null) {
            throw new ConflictException("邮箱已注册");
        }
        if (usersById.putIfAbsent(user.id(), user) != null) {
            usersByEmail.remove(user.email(), user);
            throw new ConflictException("用户主键冲突");
        }
        return user;
    }

    @Override
    public UserEntity update(UserEntity user, long expectedLockVersion) {
        UserEntity updated = usersById.compute(user.id(), (id, current) -> {
            if (current == null) {
                throw new NotFoundException("用户不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return user;
        });
        usersByEmail.put(updated.email(), updated);
        return updated;
    }
}
