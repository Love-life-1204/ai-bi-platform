package com.aibi.platform.source.store;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceRecord;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.model.MetadataSnapshot;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcDatasourceStore implements DatasourceStore {

    private static final TypeReference<List<SourceObject>> OBJECT_LIST = new TypeReference<>() { };
    private static final String SELECT_BASE = """
            SELECT id, tenant_id, workspace_id, datasource_code, datasource_name, datasource_type,
                   endpoint_json, credential_ref, owner_member_id, last_test_status, last_test_at,
                   datasource_status, created_at, updated_at, lock_version
              FROM bi_datasource WHERE deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcDatasourceStore(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DatasourceRecord insert(DatasourceRecord value) {
        String sql = """
                INSERT INTO bi_datasource
                    (id, tenant_id, workspace_id, datasource_code, datasource_name, datasource_type,
                     endpoint_json, credential_ref, owner_member_id, last_test_status, last_test_at,
                     datasource_status, created_at, updated_at, lock_version)
                VALUES
                    (:id, :tenantId, :workspaceId, :code, :name, :type,
                     :endpoint, :credentialRef, :ownerMemberId, :lastTestStatus, :lastTestAt,
                     :status, :createdAt, :updatedAt, :lockVersion)
                """;
        try {
            jdbc.update(sql, parameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ApiErrorCode.DATASOURCE_CODE_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<DatasourceRecord> find(long tenantId, long datasourceId) {
        List<DatasourceRecord> values = jdbc.query(SELECT_BASE + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", datasourceId), this::mapDatasource);
        return values.stream().findFirst();
    }

    @Override
    public List<DatasourceRecord> list(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        MapSqlParameterSource parameters = new MapSqlParameterSource("tenantId", tenantId)
                .addValue("workspaceId", workspaceId);
        return jdbc.query(SELECT_BASE + " AND tenant_id=:tenantId" + filter + " ORDER BY created_at DESC",
                parameters, this::mapDatasource);
    }

    @Override
    public DatasourceRecord update(DatasourceRecord value, long expectedVersion) {
        String sql = """
                UPDATE bi_datasource
                   SET datasource_name=:name, endpoint_json=:endpoint, credential_ref=:credentialRef,
                       datasource_status=:status, updated_at=:updatedAt, lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedVersion AND deleted_at IS NULL
                """;
        MapSqlParameterSource parameters = parameters(value).addValue("expectedVersion", expectedVersion);
        if (jdbc.update(sql, parameters) != 1) {
            throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return value;
    }

    @Override
    public DatasourceRecord recordConnectionTest(long tenantId, long datasourceId, String status, Instant testedAt) {
        jdbc.update("""
                UPDATE bi_datasource SET last_test_status=:status, last_test_at=:testedAt
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, new MapSqlParameterSource("tenantId", tenantId).addValue("id", datasourceId)
                .addValue("status", status).addValue("testedAt", Timestamp.from(testedAt)));
        return find(tenantId, datasourceId).orElseThrow(() -> new NotFoundException("数据源不存在"));
    }

    @Override
    public boolean softDelete(long tenantId, long datasourceId, long expectedVersion) {
        return jdbc.update("""
                UPDATE bi_datasource SET deleted_at=UTC_TIMESTAMP(3), credential_ref=NULL,
                       lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedVersion
                   AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", datasourceId,
                "expectedVersion", expectedVersion)) == 1;
    }

    @Override
    public MetadataSnapshot saveSnapshot(MetadataSnapshot snapshot) {
        MetadataSnapshot persisted = persistObjects(snapshot);
        String sql = """
                INSERT INTO bi_metadata_snapshot
                    (id, tenant_id, datasource_id, snapshot_version, schema_hash, snapshot_json,
                     collected_at, created_at, updated_at, lock_version)
                VALUES
                    (:id, :tenantId, :datasourceId, :version, :schemaHash, :snapshot,
                     :collectedAt, :collectedAt, :collectedAt, 0)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", persisted.id()).addValue("tenantId", persisted.tenantId())
                .addValue("datasourceId", persisted.datasourceId()).addValue("version", persisted.version())
                .addValue("schemaHash", persisted.schemaHash()).addValue("snapshot", writeJson(persisted.objects()))
                .addValue("collectedAt", Timestamp.from(persisted.collectedAt())));
        return persisted;
    }

    @Override
    public List<MetadataSnapshot> listSnapshots(long tenantId, long datasourceId) {
        String sql = """
                SELECT id, tenant_id, datasource_id, snapshot_version, schema_hash, snapshot_json, collected_at
                  FROM bi_metadata_snapshot
                 WHERE tenant_id=:tenantId AND datasource_id=:datasourceId AND deleted_at IS NULL
                 ORDER BY snapshot_version
                """;
        return jdbc.query(sql, Map.of("tenantId", tenantId, "datasourceId", datasourceId), (result, row) -> {
            try {
                List<SourceObject> objects = objectMapper.readValue(result.getString("snapshot_json"), OBJECT_LIST);
                objects = attachPersistentIds(result.getLong("tenant_id"), result.getLong("datasource_id"), objects);
                return new MetadataSnapshot(result.getLong("id"), result.getLong("tenant_id"),
                        result.getLong("datasource_id"), result.getLong("snapshot_version"),
                        result.getString("schema_hash"), objects,
                        result.getTimestamp("collected_at").toInstant());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("元数据快照无法解析", exception);
            }
        });
    }

    private List<SourceObject> attachPersistentIds(long tenantId, long datasourceId, List<SourceObject> objects) {
        Map<String, Long> objectIds = new HashMap<>();
        jdbc.query("""
                SELECT id, schema_name, object_name
                  FROM bi_source_object
                 WHERE tenant_id=:tenantId AND datasource_id=:datasourceId AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "datasourceId", datasourceId), result -> {
                    objectIds.put(objectKey(result.getString("schema_name"),
                            result.getString("object_name")), result.getLong("id"));
                });

        Map<String, Long> columnIds = new HashMap<>();
        jdbc.query("""
                SELECT c.id, c.source_object_id, c.column_name
                  FROM bi_source_column c
                  JOIN bi_source_object o ON o.id=c.source_object_id AND o.tenant_id=c.tenant_id
                 WHERE c.tenant_id=:tenantId AND o.datasource_id=:datasourceId
                   AND c.deleted_at IS NULL AND o.deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "datasourceId", datasourceId), result -> {
                    columnIds.put(columnKey(result.getLong("source_object_id"),
                            result.getString("column_name")), result.getLong("id"));
                });

        return objects.stream().map(object -> {
            Long objectId = object.id() == null
                    ? objectIds.get(objectKey(object.schema(), object.name())) : object.id();
            List<SourceColumn> columns = object.columns().stream().map(column -> {
                Long columnId = column.id();
                if (columnId == null && objectId != null) {
                    columnId = columnIds.get(columnKey(objectId, column.name()));
                }
                return new SourceColumn(columnId, column.name(), column.stableKey(), column.ordinalPosition(),
                        column.dataType(), column.nullable(), column.primaryKey(), column.comment());
            }).toList();
            return new SourceObject(objectId, object.schema(), object.name(), object.type(), object.comment(),
                    object.schemaHash(), columns);
        }).toList();
    }

    private String objectKey(String schema, String name) {
        return schema + '\0' + name;
    }

    private String columnKey(long objectId, String name) {
        return objectId + "\0" + name;
    }

    private MetadataSnapshot persistObjects(MetadataSnapshot snapshot) {
        List<SourceObject> persisted = new java.util.ArrayList<>();
        for (SourceObject object : snapshot.objects()) {
            long objectId = findOrCreateObject(snapshot, object);
            List<SourceColumn> columns = new java.util.ArrayList<>();
            for (SourceColumn column : object.columns()) {
                columns.add(upsertColumn(snapshot.tenantId(), objectId, column));
            }
            persisted.add(new SourceObject(objectId, object.schema(), object.name(), object.type(),
                    object.comment(), object.schemaHash(), List.copyOf(columns)));
        }
        return new MetadataSnapshot(snapshot.id(), snapshot.tenantId(), snapshot.datasourceId(),
                snapshot.version(), snapshot.schemaHash(), List.copyOf(persisted), snapshot.collectedAt());
    }

    private long findOrCreateObject(MetadataSnapshot snapshot, SourceObject object) {
        String upsert = """
                INSERT INTO bi_source_object
                    (id, tenant_id, datasource_id, schema_name, object_name, object_type, schema_hash,
                     metadata_json, created_at, updated_at, lock_version)
                VALUES
                    (:id, :tenantId, :datasourceId, :schema, :name, :type, :hash,
                     :metadata, :now, :now, 0)
                ON DUPLICATE KEY UPDATE object_type=VALUES(object_type), schema_hash=VALUES(schema_hash),
                    metadata_json=VALUES(metadata_json), updated_at=VALUES(updated_at), lock_version=lock_version+1
                """;
        long proposedId = object.id() == null ? newId() : object.id();
        jdbc.update(upsert, new MapSqlParameterSource()
                .addValue("id", proposedId).addValue("tenantId", snapshot.tenantId())
                .addValue("datasourceId", snapshot.datasourceId()).addValue("schema", object.schema())
                .addValue("name", object.name()).addValue("type", object.type()).addValue("hash", object.schemaHash())
                .addValue("metadata", writeJson(object)).addValue("now", Timestamp.from(snapshot.collectedAt())));
        Long objectId = jdbc.queryForObject("""
                SELECT id FROM bi_source_object
                 WHERE tenant_id=:tenantId AND datasource_id=:datasourceId
                   AND schema_name=:schema AND object_name=:name AND deleted_at IS NULL
                """, Map.of("tenantId", snapshot.tenantId(), "datasourceId", snapshot.datasourceId(),
                "schema", object.schema(), "name", object.name()), Long.class);
        if (objectId == null) {
            throw new NotFoundException("元数据对象保存失败");
        }
        return objectId;
    }

    private SourceColumn upsertColumn(long tenantId, long objectId, SourceColumn column) {
        String sql = """
                INSERT INTO bi_source_column
                    (id, tenant_id, source_object_id, column_name, stable_column_key, ordinal_position,
                     data_type, is_nullable, is_primary_key, sensitivity, column_comment,
                     created_at, updated_at, lock_version)
                VALUES
                    (:id, :tenantId, :objectId, :name, :stableKey, :position,
                     :dataType, :nullable, :primaryKey, 'UNKNOWN', :comment, NOW(3), NOW(3), 0)
                ON DUPLICATE KEY UPDATE stable_column_key=VALUES(stable_column_key),
                    ordinal_position=VALUES(ordinal_position), data_type=VALUES(data_type),
                    is_nullable=VALUES(is_nullable), is_primary_key=VALUES(is_primary_key),
                    column_comment=VALUES(column_comment), updated_at=NOW(3), lock_version=lock_version+1
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", column.id() == null ? newId() : column.id())
                .addValue("tenantId", tenantId).addValue("objectId", objectId)
                .addValue("name", column.name()).addValue("stableKey", column.stableKey())
                .addValue("position", column.ordinalPosition()).addValue("dataType", column.dataType())
                .addValue("nullable", column.nullable()).addValue("primaryKey", column.primaryKey())
                .addValue("comment", column.comment()));
        Long columnId = jdbc.queryForObject("""
                SELECT id FROM bi_source_column
                 WHERE tenant_id=:tenantId AND source_object_id=:objectId
                   AND column_name=:name AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "objectId", objectId, "name", column.name()), Long.class);
        if (columnId == null) {
            throw new NotFoundException("元数据字段保存失败");
        }
        return new SourceColumn(columnId, column.name(), column.stableKey(), column.ordinalPosition(),
                column.dataType(), column.nullable(), column.primaryKey(), column.comment());
    }

    private MapSqlParameterSource parameters(DatasourceRecord value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("workspaceId", value.workspaceId()).addValue("code", value.code())
                .addValue("name", value.name()).addValue("type", value.type().name())
                .addValue("endpoint", writeJson(value.endpoint())).addValue("credentialRef", value.credentialRef())
                .addValue("ownerMemberId", value.ownerMemberId()).addValue("lastTestStatus", value.lastTestStatus())
                .addValue("lastTestAt", value.lastTestAt() == null ? null : Timestamp.from(value.lastTestAt()))
                .addValue("status", value.status()).addValue("createdAt", Timestamp.from(value.createdAt()))
                .addValue("updatedAt", Timestamp.from(value.updatedAt())).addValue("lockVersion", value.lockVersion());
    }

    private DatasourceRecord mapDatasource(ResultSet result, int row) throws SQLException {
        try {
            return new DatasourceRecord(result.getLong("id"), result.getLong("tenant_id"),
                    result.getLong("workspace_id"), result.getString("datasource_code"),
                    result.getString("datasource_name"), DatasourceType.valueOf(result.getString("datasource_type")),
                    objectMapper.readValue(result.getString("endpoint_json"), DatasourceEndpoint.class),
                    result.getString("credential_ref"), nullableLong(result, "owner_member_id"),
                    result.getString("last_test_status"), nullableInstant(result, "last_test_at"),
                    result.getString("datasource_status"),
                    result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant(),
                    result.getLong("lock_version"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("数据源 endpoint_json 无法解析", exception);
        }
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private Instant nullableInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
