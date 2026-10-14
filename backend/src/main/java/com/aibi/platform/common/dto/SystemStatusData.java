package com.aibi.platform.common.dto;

import java.time.Instant;

public record SystemStatusData(String serviceName, String status, Instant checkedAt) {
}
