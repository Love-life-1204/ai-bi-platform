package com.aibi.platform.manager;

import com.aibi.platform.common.idempotency.IdempotencyClaim;
import com.aibi.platform.common.idempotency.IdempotencyScope;
import com.aibi.platform.mapper.IdempotencyRequestMapper;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyManager {

    private final IdempotencyRequestMapper mapper;

    public IdempotencyManager(IdempotencyRequestMapper mapper) {
        this.mapper = mapper;
    }

    public IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("幂等记录 TTL 必须大于 0");
        }
        return mapper.claim(scope, requestHash, ttl);
    }

    public void complete(IdempotencyScope scope, String requestHash, String responseJson) {
        mapper.complete(scope, requestHash, responseJson);
    }

    public void fail(IdempotencyScope scope, String requestHash) {
        mapper.fail(scope, requestHash);
    }
}
