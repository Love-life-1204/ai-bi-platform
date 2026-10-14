package com.aibi.platform.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class DataSourceConfiguration {

    @Bean(name = "mysqlDataSource", destroyMethod = "close")
    @Primary
    public HikariDataSource mysqlDataSource(DatabaseProperties properties) {
        return buildDataSource(properties.getMysql(), false);
    }

    @Bean(name = "clickHouseDataSource", destroyMethod = "close")
    public HikariDataSource clickHouseDataSource(DatabaseProperties properties) {
        return buildDataSource(properties.getClickhouse(), true);
    }

    @Bean(name = "clickHouseWriterDataSource", destroyMethod = "close")
    public HikariDataSource clickHouseWriterDataSource(DatabaseProperties properties) {
        return buildDataSource(properties.getClickhouseWriter(), false);
    }

    @Bean(name = "mysqlJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate mysqlJdbcTemplate(
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(30);
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean(name = "clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate(
            @Qualifier("clickHouseDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(30);
        return jdbcTemplate;
    }

    @Bean(name = "clickHouseWriterJdbcTemplate")
    public JdbcTemplate clickHouseWriterJdbcTemplate(
            @Qualifier("clickHouseWriterDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(30);
        return jdbcTemplate;
    }

    @Bean(name = "mysqlTransactionManager")
    public PlatformTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private HikariDataSource buildDataSource(DatabaseProperties.Pool properties, boolean forceReadOnly) {
        validate(properties);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setPoolName(properties.getPoolName());
        config.setReadOnly(forceReadOnly || properties.isReadOnly());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setValidationTimeout(properties.getValidationTimeoutMs());
        config.setIdleTimeout(properties.getIdleTimeoutMs());
        config.setMaxLifetime(properties.getMaxLifetimeMs());
        config.setConnectionTestQuery("SELECT 1");
        config.setRegisterMbeans(true);
        return new HikariDataSource(config);
    }

    private void validate(DatabaseProperties.Pool properties) {
        requireText(properties.getJdbcUrl(), "jdbc-url");
        requireText(properties.getUsername(), "username");
        requireText(properties.getPassword(), "password");
        requireText(properties.getDriverClassName(), "driver-class-name");
        requireText(properties.getPoolName(), "pool-name");
        if (properties.getMinimumIdle() < 0
                || properties.getMaximumPoolSize() < 1
                || properties.getMinimumIdle() > properties.getMaximumPoolSize()) {
            throw new IllegalStateException("数据库连接池大小配置不合法: " + properties.getPoolName());
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("数据库配置缺少 " + field);
        }
    }
}
