package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.SessionEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySessionMapper implements SessionMapper {

    private final ConcurrentMap<Long, SessionEntity> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sessionIdsByTokenHash = new ConcurrentHashMap<>();

    @Override
    public SessionEntity insert(SessionEntity session) {
        if (sessionIdsByTokenHash.putIfAbsent(session.refreshTokenHash(), session.id()) != null
                || sessions.putIfAbsent(session.id(), session) != null) {
            sessionIdsByTokenHash.remove(session.refreshTokenHash(), session.id());
            throw new ConflictException("会话标识冲突");
        }
        return session;
    }

    @Override
    public Optional<SessionEntity> findById(long sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Optional<SessionEntity> findByRefreshTokenHash(String tokenHash) {
        Long sessionId = sessionIdsByTokenHash.get(tokenHash);
        return sessionId == null ? Optional.empty() : findById(sessionId);
    }

    @Override
    public boolean revoke(long sessionId, Instant revokedAt) {
        final boolean[] changed = {false};
        sessions.computeIfPresent(sessionId, (id, current) -> {
            if (current.revokedAt() != null) {
                return current;
            }
            changed[0] = true;
            return new SessionEntity(current.id(), current.userId(), current.refreshTokenHash(),
                    current.expiresAt(), revokedAt, current.deviceFingerprint(), current.rotatedFromId(),
                    current.createdAt(), current.lockVersion() + 1);
        });
        return changed[0];
    }
}
