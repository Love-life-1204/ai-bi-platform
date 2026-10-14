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
public class MySqlSourceConnector extends AbstractJdbcConnector {

    public MySqlSourceConnector(TargetSecurityValidator targetValidator,
            @Value("${app.connector.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.connector.socket-timeout-ms:10000}") int socketTimeoutMs) {
        super(targetValidator, connectTimeoutMs, socketTimeoutMs);
    }

    @Override
    public DatasourceType type() {
        return DatasourceType.MYSQL;
    }

    @Override
    protected String jdbcUrl(DatasourceEndpoint endpoint, int connectTimeoutMs, int socketTimeoutMs) {
        String sslMode = endpoint.tls() ? "VERIFY_IDENTITY" : "DISABLED";
        return "jdbc:mysql://" + endpoint.host() + ":" + endpoint.port() + "/" + endpoint.database()
                + "?sslMode=" + sslMode
                + "&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=" + connectTimeoutMs
                + "&socketTimeout=" + socketTimeoutMs;
    }

    @Override
    protected String catalog(DatasourceEndpoint endpoint) {
        return endpoint.database();
    }

    @Override
    protected boolean includeSchema(String schema) {
        return schema != null && !schema.equalsIgnoreCase("information_schema")
                && !schema.equalsIgnoreCase("mysql") && !schema.equalsIgnoreCase("performance_schema")
                && !schema.equalsIgnoreCase("sys");
    }

    @Override
    protected void assertReadOnlyAccount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (result.next()) {
                if (hasWritePrivilege(result.getString(1))) {
                    throw permissionDenied();
                }
            }
        }
    }
}
