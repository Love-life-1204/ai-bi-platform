package com.aibi.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource")
public class DatabaseProperties {

    private boolean enabled;
    private Pool mysql = new Pool();
    private Pool clickhouse = new Pool();
    private Pool clickhouseWriter = new Pool();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Pool getMysql() {
        return mysql;
    }

    public void setMysql(Pool mysql) {
        this.mysql = mysql;
    }

    public Pool getClickhouse() {
        return clickhouse;
    }

    public void setClickhouse(Pool clickhouse) {
        this.clickhouse = clickhouse;
    }

    public Pool getClickhouseWriter() {
        return clickhouseWriter;
    }

    public void setClickhouseWriter(Pool clickhouseWriter) {
        this.clickhouseWriter = clickhouseWriter;
    }

    public static class Pool {

        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;
        private String poolName;
        private boolean readOnly;
        private int minimumIdle = 1;
        private int maximumPoolSize = 10;
        private long connectionTimeoutMs = 3000;
        private long validationTimeoutMs = 1000;
        private long idleTimeoutMs = 600000;
        private long maxLifetimeMs = 1800000;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getValidationTimeoutMs() {
            return validationTimeoutMs;
        }

        public void setValidationTimeoutMs(long validationTimeoutMs) {
            this.validationTimeoutMs = validationTimeoutMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }
    }
}
