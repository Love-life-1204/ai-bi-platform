package com.aibi.platform.source.connector;

import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.model.SourceObject;
import java.util.List;

public interface SourceConnector {
    DatasourceType type();

    ConnectorResult testConnection(DatasourceEndpoint endpoint, DatasourceCredential credential);

    List<SourceObject> fetchMetadata(long datasourceId, DatasourceEndpoint endpoint,
            DatasourceCredential credential);
}
