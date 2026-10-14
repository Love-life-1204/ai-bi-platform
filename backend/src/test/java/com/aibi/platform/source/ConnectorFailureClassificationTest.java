package com.aibi.platform.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.source.connector.AbstractJdbcConnector;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.security.TargetSecurityValidator;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import org.junit.jupiter.api.Test;

class ConnectorFailureClassificationTest {

    private final TestConnector connector = new TestConnector();

    @Test
    void shouldClassifyWrongCredentials() {
        ApiException error = connector.classify(
                new SQLException("denied password=super-secret", "28000"));
        assertThat(error.getErrorCode()).isEqualTo(ApiErrorCode.DATASOURCE_AUTH_FAILED);
        assertThat(error.getMessage()).doesNotContain("super-secret", "password=");
    }

    @Test
    void shouldClassifyNetworkTimeout() {
        ApiException error = connector.classify(new SQLTimeoutException("timeout"));
        assertThat(error.getErrorCode()).isEqualTo(ApiErrorCode.DATASOURCE_NETWORK_TIMEOUT);
    }

    @Test
    void shouldDetectWritePrivileges() {
        assertThat(connector.isWritable("GRANT SELECT, INSERT ON sales.* TO reader")).isTrue();
        assertThat(connector.isWritable("GRANT SELECT ON sales.* TO reader")).isFalse();
        assertThat(connector.permissionError().getErrorCode())
                .isEqualTo(ApiErrorCode.DATASOURCE_PERMISSION_DENIED);
    }

    private static final class TestConnector extends AbstractJdbcConnector {
        private TestConnector() {
            super(new TargetSecurityValidator(false, java.util.Set.of(3306)), 100, 100);
        }

        @Override
        public DatasourceType type() {
            return DatasourceType.MYSQL;
        }

        @Override
        protected String jdbcUrl(DatasourceEndpoint endpoint, int connectTimeoutMs, int socketTimeoutMs) {
            return "jdbc:test";
        }

        @Override
        protected void assertReadOnlyAccount(Connection connection) {
        }

        private ApiException classify(SQLException exception) {
            return translate(exception);
        }

        private boolean isWritable(String grants) {
            return hasWritePrivilege(grants);
        }

        private ApiException permissionError() {
            return permissionDenied();
        }
    }
}
