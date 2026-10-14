package com.aibi.platform.source.connector;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.source.model.DatasourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConnectorRegistry {

    private final Map<DatasourceType, SourceConnector> connectors = new EnumMap<>(DatasourceType.class);

    public ConnectorRegistry(List<SourceConnector> connectors) {
        connectors.forEach(connector -> this.connectors.put(connector.type(), connector));
    }

    public SourceConnector get(DatasourceType type) {
        SourceConnector connector = connectors.get(type);
        if (connector == null) {
            throw new BadRequestException("不支持的数据源类型");
        }
        return connector;
    }
}
