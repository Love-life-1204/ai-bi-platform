package com.aibi.platform.source.service;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import com.aibi.platform.source.connector.ConnectorRegistry;
import com.aibi.platform.source.connector.ConnectorResult;
import com.aibi.platform.source.dto.ConnectionTestData;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.dto.DatasourceData;
import com.aibi.platform.source.dto.DatasourceDependencyData;
import com.aibi.platform.source.dto.MetadataDiffData;
import com.aibi.platform.source.dto.TestConnectionRequest;
import com.aibi.platform.source.dto.UpdateDatasourceRequest;
import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.MetadataSnapshot;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.aibi.platform.source.security.CredentialStore;
import com.aibi.platform.source.store.DatasourceStore;
import com.aibi.platform.identity.service.ResourceAccessManager;
import com.aibi.platform.common.transaction.MySqlTransactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private final DatasourceStore store;
    private final CredentialStore credentials;
    private final ConnectorRegistry connectors;
    private final IngestionMapper ingestionMapper;
    private final ResourceAccessManager security;

    @Autowired
    public DatasourceServiceImpl(DatasourceStore store, CredentialStore credentials,
            ConnectorRegistry connectors, IngestionMapper ingestionMapper, ResourceAccessManager security) {
        this.store = store;
        this.credentials = credentials;
        this.connectors = connectors;
        this.ingestionMapper = ingestionMapper;
        this.security = security;
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public DatasourceData create(String authorization, long tenantId, CreateDatasourceRequest request) {
        var actor = security.requireWorkspace(authorization, tenantId, request.workspaceId(), "datasource:create");
        String credentialRef = credentials.put(request.credential());
        Instant now = Instant.now();
        DatasourceRecord value = new DatasourceRecord(newId(), tenantId, request.workspaceId(),
                request.code(), request.name().trim(), request.type(), request.endpoint(), credentialRef,
                actor.memberId(), null, null, ACTIVE, now, now, 0);
        boolean inserted = false;
        try {
            var saved = store.insert(value);
            inserted = true;
            security.register(tenantId, request.workspaceId(), actor.memberId(), "DATASOURCE", saved.id());
            return toData(saved);
        } catch (RuntimeException exception) {
            if (inserted) {
                store.softDelete(tenantId, value.id(), 0);
            }
            credentials.delete(credentialRef);
            throw exception;
        }
    }

    @Override
    public DatasourceData update(String authorization, long tenantId, long datasourceId, UpdateDatasourceRequest request) {
        DatasourceRecord current = authorized(authorization, tenantId, datasourceId, "datasource:update");
        if (DISABLED.equals(current.status())) {
            throw new ConflictException("已停用的数据源不能编辑");
        }
        String nextRef = request.credential() == null
                ? current.credentialRef() : credentials.put(request.credential());
        DatasourceRecord updated = new DatasourceRecord(current.id(), current.tenantId(), current.workspaceId(),
                current.code(), request.name().trim(), current.type(), request.endpoint(), nextRef,
                current.ownerMemberId(), current.lastTestStatus(), current.lastTestAt(), current.status(),
                current.createdAt(), Instant.now(), current.lockVersion() + 1);
        try {
            DatasourceRecord saved = store.update(updated, request.lockVersion());
            if (!nextRef.equals(current.credentialRef())) {
                credentials.delete(current.credentialRef());
            }
            return toData(saved);
        } catch (RuntimeException exception) {
            if (!nextRef.equals(current.credentialRef())) {
                credentials.delete(nextRef);
            }
            throw exception;
        }
    }

    @Override
    public List<DatasourceData> list(String authorization, long tenantId, Long workspaceId) {
        var actor = security.requireTenant(authorization, tenantId);
        var values = store.list(tenantId, workspaceId).stream()
                .filter(value -> security.can(actor, value.workspaceId(), "DATASOURCE", value.id(), "datasource:read"))
                .map(this::toData).toList();
        security.requireTenant(authorization, tenantId);
        return values;
    }

    @Override
    public DatasourceData deactivate(String authorization, long tenantId, long datasourceId, long lockVersion) {
        DatasourceRecord current = authorized(authorization, tenantId, datasourceId, "datasource:update");
        if (DISABLED.equals(current.status())) {
            return toData(current);
        }
        DatasourceRecord disabled = new DatasourceRecord(current.id(), current.tenantId(), current.workspaceId(),
                current.code(), current.name(), current.type(), current.endpoint(), current.credentialRef(),
                current.ownerMemberId(), current.lastTestStatus(), current.lastTestAt(), DISABLED,
                current.createdAt(), Instant.now(), current.lockVersion() + 1);
        DatasourceRecord saved = store.update(disabled, lockVersion);
        credentials.delete(current.credentialRef());
        return toData(saved);
    }

    @Override
    public ConnectionTestData test(String authorization, long tenantId, long datasourceId) {
        DatasourceRecord datasource = active(authorized(authorization, tenantId, datasourceId, "datasource:test"));
        Instant testedAt = Instant.now();
        try {
            ConnectorResult result = connectors.get(datasource.type()).testConnection(
                    datasource.endpoint(), credentials.read(datasource.credentialRef()));
            active(authorized(authorization, tenantId, datasourceId, "datasource:test"));
            store.recordConnectionTest(tenantId, datasourceId, "SUCCESS", testedAt);
            return new ConnectionTestData("SUCCESS", result.latencyMs(), result.databaseVersion(), "连接成功，账号为只读");
        } catch (RuntimeException exception) {
            active(authorized(authorization, tenantId, datasourceId, "datasource:test"));
            String status = exception instanceof ApiException apiException
                    ? apiException.getErrorCode().getCode() : ApiErrorCode.DATASOURCE_CONNECTION_FAILED.getCode();
            store.recordConnectionTest(tenantId, datasourceId, status, testedAt);
            throw exception;
        }
    }

    @Override
    public ConnectionTestData test(String authorization, long tenantId, TestConnectionRequest request) {
        long workspaceId = security.resolveWorkspace(authorization, tenantId, request.workspaceId(), "datasource:test");
        ConnectorResult result = connectors.get(request.type()).testConnection(request.endpoint(), request.credential());
        security.requireWorkspace(authorization, tenantId, workspaceId, "datasource:test");
        return new ConnectionTestData("SUCCESS", result.latencyMs(), result.databaseVersion(), "连接成功，账号为只读");
    }

    @Override
    public DatasourceDependencyData dependencies(String authorization, long tenantId, long datasourceId) {
        authorized(authorization, tenantId, datasourceId, "datasource:read");
        List<DatasourceDependencyData.SyncJobDependency> jobs = ingestionMapper == null ? List.of()
                : ingestionMapper.listSyncJobs(tenantId).stream()
                        .filter(job -> job.datasourceId() == datasourceId)
                        .map(job -> new DatasourceDependencyData.SyncJobDependency(
                                job.id(), job.jobCode(), job.status()))
                        .toList();
        return new DatasourceDependencyData(datasourceId, jobs);
    }

    @Override
    public void delete(String authorization, long tenantId, long datasourceId, long lockVersion) {
        DatasourceRecord current = authorized(authorization, tenantId, datasourceId, "datasource:delete");
        if (!DISABLED.equals(current.status())) {
            throw new ConflictException("请先停用数据源再删除");
        }
        if (!dependencies(authorization, tenantId, datasourceId).syncJobs().isEmpty()) {
            throw new ConflictException("数据源仍被同步任务依赖，不能删除");
        }
        if (!store.softDelete(tenantId, datasourceId, lockVersion)) {
            throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        credentials.delete(current.credentialRef());
    }

    @Override
    public MetadataSnapshot collectMetadata(String authorization, long tenantId, long datasourceId) {
        DatasourceRecord datasource = active(authorized(authorization, tenantId, datasourceId, "datasource:read"));
        List<SourceObject> objects = connectors.get(datasource.type()).fetchMetadata(
                datasource.id(), datasource.endpoint(), credentials.read(datasource.credentialRef()));
        active(authorized(authorization, tenantId, datasourceId, "datasource:read"));
        List<MetadataSnapshot> previous = store.listSnapshots(tenantId, datasourceId);
        long version = previous.isEmpty() ? 1 : previous.get(previous.size() - 1).version() + 1;
        MetadataSnapshot snapshot = new MetadataSnapshot(newId(), tenantId, datasourceId, version,
                snapshotHash(objects), objects, Instant.now());
        return store.saveSnapshot(snapshot);
    }

    @Override
    public List<MetadataSnapshot> snapshots(String authorization, long tenantId, long datasourceId) {
        authorized(authorization, tenantId, datasourceId, "datasource:read");
        return store.listSnapshots(tenantId, datasourceId);
    }

    @Override
    public MetadataDiffData diff(String authorization, long tenantId, long datasourceId, long fromVersion, long toVersion) {
        authorized(authorization, tenantId, datasourceId, "datasource:read");
        if (fromVersion >= toVersion) {
            throw new BadRequestException("fromVersion 必须小于 toVersion");
        }
        Map<Long, MetadataSnapshot> versions = store.listSnapshots(tenantId, datasourceId).stream()
                .collect(Collectors.toMap(MetadataSnapshot::version, Function.identity()));
        MetadataSnapshot from = versions.get(fromVersion);
        MetadataSnapshot to = versions.get(toVersion);
        if (from == null || to == null) {
            throw new NotFoundException("指定的元数据版本不存在");
        }
        return compare(from, to);
    }

    private MetadataDiffData compare(MetadataSnapshot from, MetadataSnapshot to) {
        Map<String, SourceObject> oldObjects = objectMap(from.objects());
        Map<String, SourceObject> newObjects = objectMap(to.objects());
        Set<String> oldNames = oldObjects.keySet();
        Set<String> newNames = newObjects.keySet();
        List<String> addedObjects = difference(newNames, oldNames);
        List<String> removedObjects = difference(oldNames, newNames);
        List<String> changedObjects = new ArrayList<>();
        List<String> addedColumns = new ArrayList<>();
        List<String> removedColumns = new ArrayList<>();
        List<String> changedColumns = new ArrayList<>();
        for (String name : intersection(oldNames, newNames)) {
            SourceObject oldObject = oldObjects.get(name);
            SourceObject newObject = newObjects.get(name);
            if (!oldObject.schemaHash().equals(newObject.schemaHash())) {
                changedObjects.add(name);
            }
            compareColumns(name, oldObject, newObject, addedColumns, removedColumns, changedColumns);
        }
        return new MetadataDiffData(from.version(), to.version(), addedObjects, removedObjects,
                sorted(changedObjects), sorted(addedColumns), sorted(removedColumns), sorted(changedColumns));
    }

    private void compareColumns(String objectName, SourceObject oldObject, SourceObject newObject,
            List<String> added, List<String> removed, List<String> changed) {
        Map<String, SourceColumn> oldColumns = oldObject.columns().stream()
                .collect(Collectors.toMap(SourceColumn::name, Function.identity()));
        Map<String, SourceColumn> newColumns = newObject.columns().stream()
                .collect(Collectors.toMap(SourceColumn::name, Function.identity()));
        difference(newColumns.keySet(), oldColumns.keySet()).forEach(name -> added.add(objectName + "." + name));
        difference(oldColumns.keySet(), newColumns.keySet()).forEach(name -> removed.add(objectName + "." + name));
        for (String name : intersection(oldColumns.keySet(), newColumns.keySet())) {
            SourceColumn oldColumn = oldColumns.get(name);
            SourceColumn newColumn = newColumns.get(name);
            if (!oldColumn.dataType().equals(newColumn.dataType())
                    || oldColumn.nullable() != newColumn.nullable()
                    || oldColumn.primaryKey() != newColumn.primaryKey()) {
                changed.add(objectName + "." + name);
            }
        }
    }

    private Map<String, SourceObject> objectMap(List<SourceObject> objects) {
        Map<String, SourceObject> values = new HashMap<>();
        objects.forEach(object -> values.put(object.schema() + "." + object.name(), object));
        return values;
    }

    private List<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return sorted(result);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private List<String> sorted(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private DatasourceRecord required(long tenantId, long datasourceId) {
        return store.find(tenantId, datasourceId).orElseThrow(() -> new NotFoundException("数据源不存在"));
    }

    private DatasourceRecord authorized(String authorization, long tenantId, long id, String permission) {
        security.requireAnyWorkspacePermission(authorization, tenantId, permission);
        var datasource = required(tenantId, id);
        security.require(authorization, tenantId, datasource.workspaceId(), "DATASOURCE", id, permission);
        return datasource;
    }

    private DatasourceRecord active(DatasourceRecord datasource) {
        if (DISABLED.equals(datasource.status())) {
            throw new ConflictException("数据源已停用");
        }
        return datasource;
    }

    private DatasourceData toData(DatasourceRecord value) {
        return new DatasourceData(value.id(), value.workspaceId(), value.code(), value.name(), value.type(),
                value.endpoint(), value.credentialRef() != null, value.ownerMemberId(), value.lastTestStatus(),
                value.lastTestAt(), value.status(), value.createdAt(), value.updatedAt(),
                value.lockVersion());
    }

    private String snapshotHash(List<SourceObject> objects) {
        String canonical = objects.stream()
                .sorted(Comparator.comparing(SourceObject::schema).thenComparing(SourceObject::name))
                .map(object -> object.schema() + "\u0000" + object.name() + "\u0000" + object.schemaHash())
                .collect(Collectors.joining("\u0001"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
