package com.aibi.platform.source.connector;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.aibi.platform.source.security.TargetSecurityValidator;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;

public abstract class AbstractJdbcConnector implements SourceConnector {

    private final TargetSecurityValidator targetValidator;
    private final int connectTimeoutMs;
    private final int socketTimeoutMs;

    protected AbstractJdbcConnector(TargetSecurityValidator targetValidator,
            @Value("${app.connector.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.connector.socket-timeout-ms:10000}") int socketTimeoutMs) {
        this.targetValidator = targetValidator;
        this.connectTimeoutMs = connectTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
    }

    @Override
    public ConnectorResult testConnection(DatasourceEndpoint endpoint, DatasourceCredential credential) {
        Set<String> approvedAddresses = targetValidator.validateAndResolve(endpoint);
        Instant started = Instant.now();
        try (Connection connection = open(endpoint, credential)) {
            configureReadOnly(connection);
            assertReadOnlyAccount(connection);
            targetValidator.verifyDnsUnchanged(endpoint, approvedAddresses);
            String version = connection.getMetaData().getDatabaseProductVersion();
            connection.rollback();
            return new ConnectorResult(Duration.between(started, Instant.now()).toMillis(), version);
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<SourceObject> fetchMetadata(long datasourceId, DatasourceEndpoint endpoint,
            DatasourceCredential credential) {
        Set<String> approvedAddresses = targetValidator.validateAndResolve(endpoint);
        try (Connection connection = open(endpoint, credential)) {
            configureReadOnly(connection);
            assertReadOnlyAccount(connection);
            List<SourceObject> objects = readMetadata(connection, datasourceId, endpoint);
            targetValidator.verifyDnsUnchanged(endpoint, approvedAddresses);
            connection.rollback();
            return objects;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    protected abstract String jdbcUrl(DatasourceEndpoint endpoint, int connectTimeoutMs, int socketTimeoutMs);

    protected abstract void assertReadOnlyAccount(Connection connection) throws SQLException;

    protected String catalog(DatasourceEndpoint endpoint) {
        return null;
    }

    protected String schemaPattern(DatasourceEndpoint endpoint) {
        return null;
    }

    protected boolean includeSchema(String schema) {
        return schema != null && !schema.equalsIgnoreCase("information_schema");
    }

    private Connection open(DatasourceEndpoint endpoint, DatasourceCredential credential) throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(endpoint, connectTimeoutMs, socketTimeoutMs),
                credential.username(),
                credential.password());
    }

    private void configureReadOnly(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    }

    private List<SourceObject> readMetadata(Connection connection, long datasourceId,
            DatasourceEndpoint endpoint) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<SourceObject> objects = new ArrayList<>();
        try (ResultSet tables = metadata.getTables(catalog(endpoint), schemaPattern(endpoint), "%",
                new String[] {"TABLE", "VIEW"})) {
            while (tables.next()) {
                String tableCatalog = tables.getString("TABLE_CAT");
                String tableSchema = tables.getString("TABLE_SCHEM");
                String schema = tableSchema == null || tableSchema.isBlank() ? tableCatalog : tableSchema;
                if (!includeSchema(schema)) {
                    continue;
                }
                String tableName = tables.getString("TABLE_NAME");
                String tableType = tables.getString("TABLE_TYPE");
                String comment = tables.getString("REMARKS");
                List<SourceColumn> columns = readColumns(
                        metadata, datasourceId, tableCatalog, tableSchema, schema, tableName);
                objects.add(new SourceObject(schema, tableName, tableType, comment,
                        objectHash(schema, tableName, tableType, columns), columns));
            }
        }
        objects.sort(Comparator.comparing(SourceObject::schema).thenComparing(SourceObject::name));
        return List.copyOf(objects);
    }

    private List<SourceColumn> readColumns(DatabaseMetaData metadata, long datasourceId, String catalog,
            String sourceSchema, String schema, String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet keys = metadata.getPrimaryKeys(catalog, sourceSchema, tableName)) {
            while (keys.next()) {
                primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
        }
        List<SourceColumn> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(catalog, sourceSchema, tableName, "%")) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                int size = result.getInt("COLUMN_SIZE");
                int scale = result.getInt("DECIMAL_DIGITS");
                String type = normalizedType(result.getString("TYPE_NAME"), size, scale);
                int nullable = result.getInt("NULLABLE");
                columns.add(new SourceColumn(
                        name,
                        stableKey(datasourceId, schema, tableName, name),
                        result.getInt("ORDINAL_POSITION"),
                        type,
                        nullable != DatabaseMetaData.columnNoNulls,
                        primaryKeys.contains(name),
                        result.getString("REMARKS")));
            }
        }
        columns.sort(Comparator.comparingInt(SourceColumn::ordinalPosition));
        return List.copyOf(columns);
    }

    private String normalizedType(String name, int size, int scale) {
        String value = name == null ? "UNKNOWN" : name.toUpperCase(Locale.ROOT);
        if (size <= 0) {
            return value;
        }
        return scale > 0 ? value + "(" + size + "," + scale + ")" : value + "(" + size + ")";
    }

    private String stableKey(long datasourceId, String schema, String table, String column) {
        return hash(datasourceId + "\u0000" + schema + "\u0000" + table + "\u0000" + column);
    }

    private String objectHash(String schema, String table, String type, List<SourceColumn> columns) {
        StringBuilder canonical = new StringBuilder(schema).append('\u0000').append(table).append('\u0000').append(type);
        for (SourceColumn column : columns) {
            canonical.append('\u0000').append(column.name())
                    .append('\u0000').append(column.dataType())
                    .append('\u0000').append(column.nullable())
                    .append('\u0000').append(column.primaryKey())
                    .append('\u0000').append(column.comment());
        }
        return hash(canonical.toString());
    }

    protected ApiException permissionDenied() {
        return new ApiException(ApiErrorCode.DATASOURCE_PERMISSION_DENIED, "源账号包含写权限，请改用只读账号");
    }

    protected boolean hasWritePrivilege(String grants) {
        String normalized = grants.toUpperCase(Locale.ROOT);
        return normalized.contains("ALL PRIVILEGES")
                || normalized.matches(".*\\b(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE|REFERENCES|TRIGGER"
                        + "|EXECUTE|EVENT|FILE|SUPER|RELOAD|SHUTDOWN|LOCK TABLES|CREATE USER)\\b.*");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    protected ApiException translate(SQLException exception) {
        String state = exception.getSQLState();
        if (state != null && state.startsWith("28")) {
            return new ApiException(ApiErrorCode.DATASOURCE_AUTH_FAILED, "用户名或密码错误");
        }
        if (exception instanceof SQLTimeoutException || hasCause(exception, SocketTimeoutException.class)
                || state != null && (state.equals("HYT00") || state.equals("HYT01"))) {
            return new ApiException(ApiErrorCode.DATASOURCE_NETWORK_TIMEOUT, "连接数据源超时");
        }
        return new ApiException(ApiErrorCode.DATASOURCE_CONNECTION_FAILED, "无法连接数据源");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
