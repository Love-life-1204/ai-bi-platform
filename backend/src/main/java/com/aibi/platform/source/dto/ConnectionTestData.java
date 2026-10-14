package com.aibi.platform.source.dto;

public record ConnectionTestData(String status, long latencyMs, String databaseVersion, String message) {
}
