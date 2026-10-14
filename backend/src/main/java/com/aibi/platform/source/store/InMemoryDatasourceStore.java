package com.aibi.platform.source.store;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.MetadataSnapshot;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDatasourceStore implements DatasourceStore {

    private final Map<Long, DatasourceRecord> records = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByCode = new ConcurrentHashMap<>();
    private final Map<Long, List<MetadataSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong metadataIds = new AtomicLong(1_000_000);

    @Override
    public DatasourceRecord insert(DatasourceRecord datasource) {
        String key = datasource.tenantId() + ":" + datasource.code();
        if (idsByCode.putIfAbsent(key, datasource.id()) != null) {
            throw new ApiException(ApiErrorCode.DATASOURCE_CODE_ALREADY_EXISTS);
        }
        records.put(datasource.id(), datasource);
        return datasource;
    }

    @Override
    public Optional<DatasourceRecord> find(long tenantId, long datasourceId) {
        return Optional.ofNullable(records.get(datasourceId)).filter(record -> record.tenantId() == tenantId);
    }

    @Override
    public List<DatasourceRecord> list(long tenantId, Long workspaceId) {
        return records.values().stream()
                .filter(record -> record.tenantId() == tenantId)
                .filter(record -> workspaceId == null || record.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(DatasourceRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public DatasourceRecord update(DatasourceRecord datasource, long expectedVersion) {
        records.compute(datasource.id(), (id, current) -> {
            if (current == null || current.tenantId() != datasource.tenantId()) {
                throw new ConflictException("数据源不存在或已被删除");
            }
            if (current.lockVersion() != expectedVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return datasource;
        });
        return datasource;
    }

    @Override
    public DatasourceRecord recordConnectionTest(long tenantId, long datasourceId, String status, Instant testedAt) {
        return records.compute(datasourceId, (id, current) -> {
            if (current == null || current.tenantId() != tenantId) {
                throw new ConflictException("数据源不存在或已被删除");
            }
            return new DatasourceRecord(current.id(), current.tenantId(), current.workspaceId(),
                    current.code(), current.name(), current.type(), current.endpoint(), current.credentialRef(),
                    current.ownerMemberId(), status, testedAt, current.status(), current.createdAt(),
                    current.updatedAt(), current.lockVersion());
        });
    }

    @Override
    public boolean softDelete(long tenantId, long datasourceId, long expectedVersion) {
        DatasourceRecord current = records.get(datasourceId);
        if (current == null || current.tenantId() != tenantId || current.lockVersion() != expectedVersion) {
            return false;
        }
        if (!records.remove(datasourceId, current)) {
            return false;
        }
        idsByCode.remove(tenantId + ":" + current.code(), datasourceId);
        snapshots.remove(datasourceId);
        return true;
    }

    @Override
    public MetadataSnapshot saveSnapshot(MetadataSnapshot snapshot) {
        List<SourceObject> objects = snapshot.objects().stream().map(object -> {
            long objectId = object.id() == null ? metadataIds.incrementAndGet() : object.id();
            List<SourceColumn> columns = object.columns().stream().map(column -> new SourceColumn(
                    column.id() == null ? metadataIds.incrementAndGet() : column.id(),
                    column.name(), column.stableKey(), column.ordinalPosition(), column.dataType(),
                    column.nullable(), column.primaryKey(), column.comment())).toList();
            return new SourceObject(objectId, object.schema(), object.name(), object.type(),
                    object.comment(), object.schemaHash(), columns);
        }).toList();
        MetadataSnapshot persisted = new MetadataSnapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.datasourceId(), snapshot.version(), snapshot.schemaHash(), objects,
                snapshot.collectedAt());
        snapshots.computeIfAbsent(snapshot.datasourceId(), ignored -> new CopyOnWriteArrayList<>()).add(persisted);
        return persisted;
    }

    @Override
    public List<MetadataSnapshot> listSnapshots(long tenantId, long datasourceId) {
        List<MetadataSnapshot> result = new ArrayList<>(snapshots.getOrDefault(datasourceId, List.of()));
        result.removeIf(snapshot -> snapshot.tenantId() != tenantId);
        result.sort(Comparator.comparingLong(MetadataSnapshot::version));
        return List.copyOf(result);
    }
}
