package com.aibi.platform.source.connector;

import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.security.TargetSecurityValidator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlSourceConnector extends AbstractJdbcConnector {

    public PostgreSqlSourceConnector(TargetSecurityValidator targetValidator,
            @Value("${app.connector.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.connector.socket-timeout-ms:10000}") int socketTimeoutMs) {
        super(targetValidator, connectTimeoutMs, socketTimeoutMs);
    }

    @Override
    public DatasourceType type() {
        return DatasourceType.POSTGRESQL;
    }

    @Override
    protected String jdbcUrl(DatasourceEndpoint endpoint, int connectTimeoutMs, int socketTimeoutMs) {
        String sslMode = endpoint.tls() ? "verify-full" : "disable";
        return "jdbc:postgresql://" + endpoint.host() + ":" + endpoint.port() + "/" + endpoint.database()
                + "?sslmode=" + sslMode
                + "&connectTimeout=" + Math.max(1, connectTimeoutMs / 1000)
                + "&socketTimeout=" + Math.max(1, socketTimeoutMs / 1000);
    }

    @Override
    protected boolean includeSchema(String schema) {
        return schema != null && !schema.equalsIgnoreCase("information_schema")
                && !schema.startsWith("pg_");
    }

    @Override
    protected void assertReadOnlyAccount(Connection connection) throws SQLException {
        String sql = """
                SELECT has_database_privilege(current_user, current_database(), 'CREATE')
                    OR EXISTS (
                        SELECT 1 FROM information_schema.role_table_grants
                        WHERE grantee = current_user
                          AND privilege_type IN ('INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER')
                    )
                """;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (result.next() && result.getBoolean(1)) {
                throw permissionDenied();
            }
        }
    }
}
