package com.aibi.platform.source.model;

public enum DatasourceType {
    MYSQL(3306),
    POSTGRESQL(5432);

    private final int defaultPort;

    DatasourceType(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }
}
