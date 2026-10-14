package com.aibi.platform.ingestion.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import com.aibi.platform.ingestion.dto.IngestionDtos.MappingResult;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.ingestion.manager.FieldMappingManager;
import com.aibi.platform.ingestion.manager.IngestionProperties;
import com.aibi.platform.ingestion.manager.JdbcSourceTableReader;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.security.CredentialStore;
import com.aibi.platform.source.store.InMemoryDatasourceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_EXTERNAL_INGESTION_ACCEPTANCE", matches = "true")
class EnterpriseOrderClickHouseAcceptanceTest {

    @Test
    void shouldExtractEnterpriseOrderAndWriteThroughApplicationRawWriter() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceDatabase = "aibi_acceptance_" + suffix;
        String sourceTable = "enterprise_order";
        String writerUser = "aibi_acceptance_" + suffix;
        String writerPassword = UUID.randomUUID().toString();
        long tenantId = positiveId(suffix, 1_000_000_000L);
        long datasetId = positiveId(new StringBuilder(suffix).reverse().toString(), 2_000_000_000L);
        long sourceId = 91_001L;
        String businessKey = "ENT-" + suffix;

        String mysqlHost = required("MYSQL_HOST");
        int mysqlPort = Integer.parseInt(required("MYSQL_PORT"));
        String mysqlUser = required("MYSQL_USER");
        String mysqlPassword = environment("MYSQL_PASSWORD", "");
        String mysqlAdminUrl = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort
                + "/?useUnicode=true&characterEncoding=UTF-8&sslMode=DISABLED";
        URI clickhouseHttp = URI.create(required("CLICKHOUSE_HTTP_URL"));
        String clickhouseAdmin = environment("CLICKHOUSE_USER", "default");
        String clickhousePassword = environment("CLICKHOUSE_PASSWORD", "");
        JdbcTemplate clickhouse = clickhouse(clickhouseHttp, clickhouseAdmin, clickhousePassword);

        createSource(mysqlAdminUrl, mysqlUser, mysqlPassword, sourceDatabase,
                sourceTable, businessKey);
        createWriter(clickhouse, writerUser, writerPassword);
        try {
            FieldMappingManager mappingManager = new FieldMappingManager();
            InMemoryDatasourceStore sources = new InMemoryDatasourceStore();
            String credentialRef = "acceptance://mysql";
            sources.insert(new DatasourceRecord(sourceId, tenantId, 1, "acceptance-source",
                    "企业订单验收源", DatasourceType.MYSQL,
                    new DatasourceEndpoint(mysqlHost, mysqlPort, sourceDatabase, false),
                    credentialRef, null, null, null, "ACTIVE", Instant.now(), Instant.now(), 0));
            CredentialStore credentials = credentials(credentialRef,
                    new DatasourceCredential(mysqlUser, mysqlPassword));
            JdbcSourceTableReader reader = new JdbcSourceTableReader(sources, credentials,
                    mappingManager);
            SyncJobEntity job = job(tenantId, datasetId, sourceId, sourceDatabase,
                    sourceTable);

            SourcePage page = reader.read(job, null, null, 100);
            assertEquals(1, page.rows().size());
            MappingResult mapped = mappingManager.map(job, page.rows().get(0));
            assertEquals(businessKey, mapped.businessKey());

            JdbcTemplate writer = clickhouse(clickhouseHttp, writerUser, writerPassword);
            JdbcIngestionMapper mapper = mapper(writer);
            StagedRowEntity event = new StagedRowEntity(new IdempotentEventKey(
                    tenantId, datasetId, sourceId, 1, 1, mapped.businessKey(),
                    mapped.sourceVersion()), 1, 1, mapped.values(),
                    new BigDecimal("125.50"), mapped.updatedAt(), false);
            mapper.writeClickHouse(event);

            Map<String, Object> written = clickhouse.queryForMap("""
                    SELECT count() AS row_count, any(payload_json) AS payload
                      FROM ai_bi_raw.ingest_event
                     WHERE tenant_id=? AND dataset_id=? AND source_id=? AND business_key=?
                    """, tenantId, datasetId, sourceId, businessKey);
            assertEquals(1L, ((Number) written.get("row_count")).longValue());
            String payload = written.get("payload").toString();
            assertTrue(payload.contains(businessKey));
            assertTrue(payload.contains("125.50"));
        } finally {
            cleanupClickHouse(clickhouse, tenantId, datasetId, writerUser);
            dropSource(mysqlAdminUrl, mysqlUser, mysqlPassword, sourceDatabase);
        }
    }

    private SyncJobEntity job(long tenantId, long datasetId, long sourceId,
            String sourceDatabase, String sourceTable) {
        Instant now = Instant.now();
        return new SyncJobEntity(1, tenantId, sourceId, datasetId, "enterprise-orders",
                "INCREMENTAL", sourceDatabase, sourceTable, "order_id", "updated_at",
                "amount", List.of(
                        new FieldMapping("order_id", "order_id", "STRING", null, true),
                        new FieldMapping("amount", "amount", "DECIMAL", null, true)),
                100, null, "UTC", "ENABLED", null, null, 0, now, now);
    }

    private JdbcIngestionMapper mapper(JdbcTemplate writer) {
        IngestionProperties properties = new IngestionProperties();
        properties.setStorageDir(Path.of(System.getProperty("java.io.tmpdir"), "aibi-acceptance"));
        DataSource mysql = new DriverManagerDataSource("jdbc:mysql://127.0.0.1/unused");
        return new JdbcIngestionMapper(mock(NamedParameterJdbcTemplate.class), writer,
                new DataSourceTransactionManager(mysql),
                new ObjectMapper().findAndRegisterModules(), properties);
    }

    private JdbcTemplate clickhouse(URI endpoint, String username, String password) {
        int port = endpoint.getPort() < 0 ? 8123 : endpoint.getPort();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        source.setUrl("jdbc:clickhouse://" + endpoint.getHost() + ":" + port
                + "/ai_bi_raw?compress=0");
        source.setUsername(username);
        source.setPassword(password);
        return new JdbcTemplate(source);
    }

    private CredentialStore credentials(String reference, DatasourceCredential credential) {
        return new CredentialStore() {
            @Override
            public String put(DatasourceCredential ignored) {
                return reference;
            }

            @Override
            public DatasourceCredential read(String actualReference) {
                assertEquals(reference, actualReference);
                return credential;
            }

            @Override
            public void delete(String ignored) {
                // 验收凭据只存在测试进程内。
            }
        };
    }

    private void createSource(String url, String user, String password, String database,
            String table, String businessKey) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "`");
            statement.execute("CREATE TABLE `" + database + "`.`" + table + "` ("
                    + "order_id VARCHAR(64) PRIMARY KEY, amount DECIMAL(20,2) NOT NULL,"
                    + "updated_at DATETIME(3) NOT NULL, is_deleted TINYINT NOT NULL DEFAULT 0)");
            statement.execute("INSERT INTO `" + database + "`.`" + table + "` VALUES ('"
                    + businessKey + "',125.50,'2026-10-11 00:00:00.000',0)");
        }
    }

    private void dropSource(String url, String user, String password, String database)
            throws Exception {
        if (!database.startsWith("aibi_acceptance_")) {
            throw new IllegalStateException("拒绝删除非验收数据库");
        }
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    private void createWriter(JdbcTemplate clickhouse, String username, String password) {
        clickhouse.execute("CREATE USER " + username
                + " IDENTIFIED WITH sha256_password BY '" + password + "'");
        clickhouse.execute("GRANT aibi_ingestion_role TO " + username);
        clickhouse.execute("ALTER USER " + username + " DEFAULT ROLE aibi_ingestion_role");
    }

    private void cleanupClickHouse(JdbcTemplate clickhouse, long tenantId,
            long datasetId, String username) {
        clickhouse.execute("ALTER TABLE ai_bi_raw.ingest_event DELETE WHERE tenant_id="
                + tenantId + " AND dataset_id=" + datasetId + " SETTINGS mutations_sync=2");
        if (!username.startsWith("aibi_acceptance_")) {
            throw new IllegalStateException("拒绝删除非验收用户");
        }
        clickhouse.execute("DROP USER IF EXISTS " + username);
    }

    private long positiveId(String value, long offset) {
        return Integer.toUnsignedLong(value.hashCode()) + offset;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少验收环境变量: " + name);
        }
        return value;
    }

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
