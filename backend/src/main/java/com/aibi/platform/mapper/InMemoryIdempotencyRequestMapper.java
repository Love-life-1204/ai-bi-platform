package com.aibi.platform.mapper;

import com.aibi.platform.common.idempotency.IdempotencyClaim;
import com.aibi.platform.common.idempotency.IdempotencyScope;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIdempotencyRequestMapper implements IdempotencyRequestMapper {

    private final ConcurrentHashMap<IdempotencyScope, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Duration ttl) {
        Instant now = Instant.now();
        AtomicReference<IdempotencyClaim> result = new AtomicReference<>();
        entries.compute(scope, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                result.set(IdempotencyClaim.acquired());
                return new Entry(requestHash, "PENDING", null, now.plus(ttl));
            }
            if (!existing.requestHash().equals(requestHash)) {
                result.set(IdempotencyClaim.hashConflict());
                return existing;
            }
            if ("FAILED".equals(existing.status())) {
                result.set(IdempotencyClaim.acquired());
                return new Entry(requestHash, "PENDING", null, now.plus(ttl));
            }
            if ("COMPLETED".equals(existing.status())) {
                result.set(IdempotencyClaim.replay(existing.responseJson()));
                return existing;
            }
            result.set(IdempotencyClaim.inProgress());
            return existing;
        });
        return result.get();
    }

    @Override
    public void complete(IdempotencyScope scope, String requestHash, String responseJson) {
        entries.computeIfPresent(scope, (ignored, existing) -> {
            if (!existing.requestHash().equals(requestHash) || !"PENDING".equals(existing.status())) {
                throw new IllegalStateException("幂等记录状态已变更");
            }
            return new Entry(requestHash, "COMPLETED", responseJson, existing.expiresAt());
        });
    }

    @Override
    public void fail(IdempotencyScope scope, String requestHash) {
        entries.computeIfPresent(scope, (ignored, existing) -> {
            if (existing.requestHash().equals(requestHash) && "PENDING".equals(existing.status())) {
                return new Entry(requestHash, "FAILED", null, existing.expiresAt());
            }
            return existing;
        });
    }

    private record Entry(String requestHash, String status, String responseJson, Instant expiresAt) {
    }
}
