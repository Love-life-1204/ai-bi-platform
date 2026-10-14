package com.aibi.platform.common.idempotency;

public record IdempotencyScope(long tenantId, long memberId, String apiRoute, String idempotencyKey) {
}
