package com.aibi.platform.mapper;

import com.aibi.platform.common.idempotency.IdempotencyClaim;
import com.aibi.platform.common.idempotency.IdempotencyScope;
import java.time.Duration;

public interface IdempotencyRequestMapper {

    IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Duration ttl);

    void complete(IdempotencyScope scope, String requestHash, String responseJson);

    void fail(IdempotencyScope scope, String requestHash);
}
