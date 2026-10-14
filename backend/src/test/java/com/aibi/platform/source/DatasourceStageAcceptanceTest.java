package com.aibi.platform.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.source.connector.ConnectorRegistry;
import com.aibi.platform.source.connector.ConnectorResult;
import com.aibi.platform.source.connector.SourceConnector;
import com.aibi.platform.source.dto.ConnectionTestData;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.dto.DatasourceData;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.model.MetadataSnapshot;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.aibi.platform.source.security.EncryptedCredentialStore;
import com.aibi.platform.source.service.DatasourceServiceImpl;
import com.aibi.platform.source.store.InMemoryDatasourceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DatasourceStageAcceptanceTest {

    private static final long ENTERPRISE_TENANT = 10001;
    private static final long OTHER_TENANT = 10002;

    @ParameterizedTest
    @EnumSource(DatasourceType.class)
    void enterpriseCanCreateValidateAndCollectControlledReadOnlyDatasource(DatasourceType type) {
        DatasourceServiceImpl service = service();
        DatasourceData datasource = service.create(DatasourceTestAccess.AUTH, ENTERPRISE_TENANT, request(type));

        ConnectionTestData connection = service.test(DatasourceTestAccess.AUTH, ENTERPRISE_TENANT, datasource.id());
        MetadataSnapshot metadata = service.collectMetadata(DatasourceTestAccess.AUTH, ENTERPRISE_TENANT, datasource.id());

        assertThat(datasource.type()).isEqualTo(type);
        assertThat(datasource.hasCredential()).isTrue();
        assertThat(connection.status()).isEqualTo("SUCCESS");
        assertThat(connection.message()).contains("只读");
        assertThat(metadata.version()).isEqualTo(1);
        assertThat(metadata.schemaHash()).hasSize(64);
        assertThat(metadata.objects()).singleElement().satisfies(object -> {
            assertThat(object.id()).isPositive();
            assertThat(object.schema()).isEqualTo("sales");
            assertThat(object.name()).isEqualTo("orders");
            assertThat(object.columns()).extracting(SourceColumn::name)
                    .containsExactly("id", "created_at");
            assertThat(object.columns().get(0).primaryKey()).isTrue();
            assertThat(object.columns().get(0).id()).isPositive();
            assertThat(object.columns().get(0).stableKey()).hasSize(64);
            assertThat(object.columns().get(0).comment()).isEqualTo("订单主键");
        });

        assertThat(service.list(DatasourceTestAccess.AUTH, OTHER_TENANT, null)).isEmpty();
        assertThatThrownBy(() -> service.test(DatasourceTestAccess.AUTH, OTHER_TENANT, datasource.id()))
                .isInstanceOf(NotFoundException.class);
    }

    private DatasourceServiceImpl service() {
        return new DatasourceServiceImpl(
                new InMemoryDatasourceStore(),
                new EncryptedCredentialStore(new ObjectMapper(), ""),
                new ConnectorRegistry(List.of(connector(DatasourceType.MYSQL),
                        connector(DatasourceType.POSTGRESQL))), null, DatasourceTestAccess.authorized());
    }

    private CreateDatasourceRequest request(DatasourceType type) {
        return new CreateDatasourceRequest(20001, type.name().toLowerCase() + "_sales", type + " 销售库", type,
                new DatasourceEndpoint("db.example.com", type.defaultPort(), "sales", true),
                new DatasourceCredential("readonly_user", "secret-value"));
    }

    private SourceConnector connector(DatasourceType type) {
        return new SourceConnector() {
            @Override
            public DatasourceType type() {
                return type;
            }

            @Override
            public ConnectorResult testConnection(DatasourceEndpoint endpoint, DatasourceCredential credential) {
                return new ConnectorResult(12, type + " test server");
            }

            @Override
            public List<SourceObject> fetchMetadata(long datasourceId, DatasourceEndpoint endpoint,
                    DatasourceCredential credential) {
                List<SourceColumn> columns = List.of(
                        new SourceColumn("id", key('a'), 1, "BIGINT", false, true, "订单主键"),
                        new SourceColumn("created_at", key('b'), 2, "TIMESTAMP", false, false, "创建时间"));
                return List.of(new SourceObject("sales", "orders", "TABLE", "订单", key('c'), columns));
            }
        };
    }

    private String key(char value) {
        return String.valueOf(value).repeat(64);
    }
}
