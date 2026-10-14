package com.aibi.platform.entity;

import java.time.Instant;

public record SystemStatusEntity(String serviceName, String status, Instant checkedAt) {
}
