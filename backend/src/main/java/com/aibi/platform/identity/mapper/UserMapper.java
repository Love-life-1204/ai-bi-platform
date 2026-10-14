package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.UserEntity;
import java.util.Optional;

public interface UserMapper {

    Optional<UserEntity> findByEmail(String normalizedEmail);

    Optional<UserEntity> findById(long userId);

    UserEntity insert(UserEntity user);

    UserEntity update(UserEntity user, long expectedLockVersion);
}
