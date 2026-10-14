package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.SessionEntity;
import java.time.Instant;
import java.util.Optional;

public interface SessionMapper {

    SessionEntity insert(SessionEntity session);

    Optional<SessionEntity> findById(long sessionId);

    Optional<SessionEntity> findByRefreshTokenHash(String tokenHash);

    boolean revoke(long sessionId, Instant revokedAt);
}
