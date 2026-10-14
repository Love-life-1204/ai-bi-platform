package com.aibi.platform.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.source.connector.ConnectorRegistry;
import com.aibi.platform.source.connector.ConnectorResult;
import com.aibi.platform.source.connector.SourceConnector;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.dto.DatasourceData;
import com.aibi.platform.source.dto.MetadataDiffData;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.aibi.platform.source.security.EncryptedCredentialStore;
import com.aibi.platform.source.service.DatasourceServiceImpl;
import com.aibi.platform.source.store.InMemoryDatasourceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MetadataVersionDiffTest {

    @Test
    void shouldVersionSnapshotsAndReportStructuralChanges() {
        AtomicInteger collection = new AtomicInteger();
        SourceConnector connector = connector(collection);
        DatasourceServiceImpl service = new DatasourceServiceImpl(
                new InMemoryDatasourceStore(),
                new EncryptedCredentialStore(new ObjectMapper(), ""),
                new ConnectorRegistry(List.of(connector)), null, DatasourceTestAccess.authorized());
        DatasourceData datasource = service.create(DatasourceTestAccess.AUTH, 71, new CreateDatasourceRequest(
                81, "sales", "销售库", DatasourceType.MYSQL,
                new DatasourceEndpoint("db.example.com", 3306, "sales", true),
                new DatasourceCredential("reader", "secret")));

        assertThat(service.collectMetadata(DatasourceTestAccess.AUTH, 71, datasource.id()).version()).isEqualTo(1);
        assertThat(service.collectMetadata(DatasourceTestAccess.AUTH, 71, datasource.id()).version()).isEqualTo(2);
        MetadataDiffData diff = service.diff(DatasourceTestAccess.AUTH, 71, datasource.id(), 1, 2);

        assertThat(diff.changedObjects()).containsExactly("sales.orders");
        assertThat(diff.addedColumns()).containsExactly("sales.orders.total_amount");
        assertThat(diff.changedColumns()).containsExactly("sales.orders.id");
    }

    private SourceConnector connector(AtomicInteger collection) {
        return new SourceConnector() {
            @Override
            public DatasourceType type() {
                return DatasourceType.MYSQL;
            }

            @Override
            public ConnectorResult testConnection(DatasourceEndpoint endpoint, DatasourceCredential credential) {
                return new ConnectorResult(1, "test");
            }

            @Override
            public List<SourceObject> fetchMetadata(long datasourceId, DatasourceEndpoint endpoint,
                    DatasourceCredential credential) {
                boolean second = collection.incrementAndGet() == 2;
                SourceColumn id = new SourceColumn("id", "stable-id", 1,
                        second ? "BIGINT(20)" : "INT(11)", false, true, "主键");
                if (second) {
                    SourceColumn amount = new SourceColumn(
                            "total_amount", "stable-amount", 2, "DECIMAL(18,2)", false, false, "金额");
                    return List.of(new SourceObject("sales", "orders", "TABLE", "订单", "hash-v2",
                            List.of(id, amount)));
                }
                return List.of(new SourceObject("sales", "orders", "TABLE", "订单", "hash-v1", List.of(id)));
            }
        };
    }
}
