package com.aibi.platform.source.store;

import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.MetadataSnapshot;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface DatasourceStore {
    DatasourceRecord insert(DatasourceRecord datasource);

    Optional<DatasourceRecord> find(long tenantId, long datasourceId);

    List<DatasourceRecord> list(long tenantId, Long workspaceId);

    DatasourceRecord update(DatasourceRecord datasource, long expectedVersion);

    DatasourceRecord recordConnectionTest(long tenantId, long datasourceId, String status, Instant testedAt);

    boolean softDelete(long tenantId, long datasourceId, long expectedVersion);

    MetadataSnapshot saveSnapshot(MetadataSnapshot snapshot);

    List<MetadataSnapshot> listSnapshots(long tenantId, long datasourceId);
}
