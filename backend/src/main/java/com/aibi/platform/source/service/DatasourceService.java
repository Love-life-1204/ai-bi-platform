package com.aibi.platform.source.service;

import com.aibi.platform.source.dto.ConnectionTestData;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.dto.DatasourceData;
import com.aibi.platform.source.dto.DatasourceDependencyData;
import com.aibi.platform.source.dto.MetadataDiffData;
import com.aibi.platform.source.dto.TestConnectionRequest;
import com.aibi.platform.source.dto.UpdateDatasourceRequest;
import com.aibi.platform.source.model.MetadataSnapshot;
import java.util.List;

public interface DatasourceService {
    DatasourceData create(String authorization, long tenantId, CreateDatasourceRequest request);

    DatasourceData update(String authorization, long tenantId, long datasourceId, UpdateDatasourceRequest request);

    List<DatasourceData> list(String authorization, long tenantId, Long workspaceId);

    DatasourceData deactivate(String authorization, long tenantId, long datasourceId, long lockVersion);

    DatasourceDependencyData dependencies(String authorization, long tenantId, long datasourceId);

    void delete(String authorization, long tenantId, long datasourceId, long lockVersion);

    ConnectionTestData test(String authorization, long tenantId, long datasourceId);

    ConnectionTestData test(String authorization, long tenantId, TestConnectionRequest request);

    MetadataSnapshot collectMetadata(String authorization, long tenantId, long datasourceId);

    List<MetadataSnapshot> snapshots(String authorization, long tenantId, long datasourceId);

    MetadataDiffData diff(String authorization, long tenantId, long datasourceId, long fromVersion, long toVersion);
}
