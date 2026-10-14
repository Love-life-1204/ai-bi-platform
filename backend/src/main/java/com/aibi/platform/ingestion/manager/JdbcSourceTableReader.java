package com.aibi.platform.ingestion.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.security.CredentialStore;
import com.aibi.platform.source.store.DatasourceStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JdbcSourceTableReader implements SourceTableReader {

    private final DatasourceStore datasourceStore;
    private final CredentialStore credentialStore;
    private final FieldMappingManager mappings;

    public JdbcSourceTableReader(DatasourceStore datasourceStore,
            CredentialStore credentialStore, FieldMappingManager mappings) {
        this.datasourceStore = datasourceStore;
        this.credentialStore = credentialStore;
        this.mappings = mappings;
    }

    @Override
    public SourcePage read(SyncJobEntity job, Instant lastUpdatedAt,
            String lastPrimaryKey, int limit) {
        DatasourceRecord datasource = datasourceStore.find(job.tenantId(), job.datasourceId())
                .orElseThrow(() -> new BadRequestException("同步任务的数据源不存在"));
        DatasourceCredential credential = credentialStore.read(datasource.credentialRef());
        String sql = buildSql(job, lastUpdatedAt, lastPrimaryKey);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(datasource),
                    credential.username(), credential.password());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(true);
            statement.setQueryTimeout(30);
            bind(statement, job, lastUpdatedAt, lastPrimaryKey, limit + 1);
            List<Map<String, Object>> rows = readRows(statement);
            boolean hasMore = rows.size() > limit;
            if (hasMore) {
                rows.remove(rows.size() - 1);
            }
            return new SourcePage(rows, hasMore);
        } catch (SQLException exception) {
            throw new IllegalStateException("源表分批抽取失败", exception);
        }
    }

    private String buildSql(SyncJobEntity job, Instant lastTime, String lastKey) {
        mappings.identifier(job.sourceSchema(), "源 Schema");
        mappings.identifier(job.sourceTable(), "源表");
        mappings.identifier(job.primaryKeyField(), "稳定主键");
        if ("INCREMENTAL".equals(job.syncMode())) {
            mappings.identifier(job.updatedAtField(), "更新时间字段");
        }
        Set<String> fields = new LinkedHashSet<>();
        job.mappings().stream().map(FieldMapping::sourceField).forEach(fields::add);
        fields.add(job.primaryKeyField());
        if (job.updatedAtField() != null && !job.updatedAtField().isBlank()) {
            fields.add(job.updatedAtField());
        }
        if (job.amountField() != null && !job.amountField().isBlank()) {
            mappings.identifier(job.amountField(), "金额字段");
            fields.add(job.amountField());
        }
        fields.forEach(field -> mappings.identifier(field, "源字段"));
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(",", fields))
                .append(" FROM ").append(job.sourceSchema()).append('.').append(job.sourceTable());
        if ("INCREMENTAL".equals(job.syncMode()) && lastTime != null) {
            sql.append(" WHERE (").append(job.updatedAtField()).append(" > ? OR (")
                    .append(job.updatedAtField()).append(" = ? AND ")
                    .append(job.primaryKeyField()).append(" > ?))");
        } else if (lastKey != null) {
            sql.append(" WHERE ").append(job.primaryKeyField()).append(" > ?");
        }
        if ("INCREMENTAL".equals(job.syncMode())) {
            sql.append(" ORDER BY ").append(job.updatedAtField()).append(" ASC,")
                    .append(job.primaryKeyField()).append(" ASC");
        } else {
            sql.append(" ORDER BY ").append(job.primaryKeyField()).append(" ASC");
        }
        return sql.append(" LIMIT ?").toString();
    }

    private void bind(PreparedStatement statement, SyncJobEntity job, Instant lastTime,
            String lastKey, int limit) throws SQLException {
        int index = 1;
        if ("INCREMENTAL".equals(job.syncMode()) && lastTime != null) {
            statement.setTimestamp(index++, Timestamp.from(lastTime));
            statement.setTimestamp(index++, Timestamp.from(lastTime));
            statement.setString(index++, lastKey == null ? "" : lastKey);
        } else if (lastKey != null) {
            statement.setString(index++, lastKey);
        }
        statement.setInt(index, limit);
    }

    private List<Map<String, Object>> readRows(PreparedStatement statement) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String jdbcUrl(DatasourceRecord datasource) {
        String protocol = datasource.type() == DatasourceType.MYSQL ? "mysql" : "postgresql";
        String ssl = datasource.type() == DatasourceType.MYSQL
                ? "?sslMode=" + (datasource.endpoint().tls() ? "REQUIRED" : "DISABLED")
                : "?sslmode=" + (datasource.endpoint().tls() ? "require" : "disable");
        return "jdbc:" + protocol + "://" + datasource.endpoint().host() + ":"
                + datasource.endpoint().port() + "/" + datasource.endpoint().database() + ssl;
    }
}
