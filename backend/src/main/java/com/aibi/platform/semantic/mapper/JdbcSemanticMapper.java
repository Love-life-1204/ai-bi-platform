package com.aibi.platform.semantic.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.aibi.platform.semantic.model.SemanticModels.Term;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
public class JdbcSemanticMapper implements SemanticMapper {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSemanticMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Dataset insertDataset(Dataset value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_dataset
                        (id,tenant_id,workspace_id,dataset_code,dataset_name,dataset_status,
                         current_version_id,owner_member_id,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:workspaceId,:code,:name,:status,
                         :currentVersionId,:ownerId,:createdAt,:updatedAt,:lockVersion)
                    """, datasetParameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("数据集编码已存在");
        }
    }

    @Override
    public Dataset updateDataset(Dataset value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_dataset SET dataset_name=:name,dataset_status=:status,
                       current_version_id=:currentVersionId,updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected AND deleted_at IS NULL
                """, datasetParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        return value;
    }

    @Override
    public Optional<Dataset> findDataset(long tenantId, long datasetId) {
        return jdbc.query(datasetSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", datasetId), this::dataset).stream().findFirst();
    }

    @Override
    public List<Dataset> listDatasets(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        return jdbc.query(datasetSelect() + " AND tenant_id=:tenantId" + filter + " ORDER BY dataset_code",
                new MapSqlParameterSource("tenantId", tenantId).addValue("workspaceId", workspaceId),
                this::dataset);
    }

    @Override
    public DatasetVersion insertDatasetVersion(DatasetVersion value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_dataset_version
                        (id,tenant_id,dataset_id,version_no,source_object_id,definition_json,
                         definition_hash,version_status,published_at,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:datasetId,:versionNo,:sourceObjectId,:definition,
                         :hash,:status,:publishedAt,NOW(3),NOW(3),:lockVersion)
                    """, datasetVersionParameters(value));
            insertFields(value);
            insertRelations(value);
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("数据集版本号已存在");
        }
    }

    @Override
    public DatasetVersion updateDatasetVersion(DatasetVersion value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_dataset_version
                   SET source_object_id=:sourceObjectId,definition_json=:definition,
                       definition_hash=:hash,version_status=:status,published_at=:publishedAt,
                       updated_at=NOW(3),lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected AND deleted_at IS NULL
                """, datasetVersionParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        if ("DRAFT".equals(value.status())) {
            jdbc.update("UPDATE bi_dataset_field SET deleted_at=NOW(3)"
                            + " WHERE tenant_id=:tenantId AND dataset_version_id=:id AND deleted_at IS NULL",
                    Map.of("tenantId", value.tenantId(), "id", value.id()));
            jdbc.update("UPDATE bi_dataset_relation SET deleted_at=NOW(3)"
                            + " WHERE tenant_id=:tenantId AND dataset_version_id=:id AND deleted_at IS NULL",
                    Map.of("tenantId", value.tenantId(), "id", value.id()));
            insertFields(value);
            insertRelations(value);
        }
        return value;
    }

    @Override
    public Optional<DatasetVersion> findDatasetVersion(long tenantId, long versionId) {
        return jdbc.query(datasetVersionSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", versionId), this::datasetVersion).stream().findFirst();
    }

    @Override
    public List<DatasetVersion> listDatasetVersions(long tenantId, long datasetId) {
        return jdbc.query(datasetVersionSelect() + " AND tenant_id=:tenantId AND dataset_id=:datasetId"
                        + " ORDER BY version_no",
                Map.of("tenantId", tenantId, "datasetId", datasetId), this::datasetVersion);
    }

    @Override
    public Metric insertMetric(Metric value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_metric
                        (id,tenant_id,workspace_id,metric_code,metric_name,metric_unit,metric_status,
                         current_version_id,owner_member_id,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:workspaceId,:code,:name,:unit,:status,
                         :currentVersionId,:ownerId,:createdAt,:updatedAt,:lockVersion)
                    """, metricParameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("指标编码已存在");
        }
    }

    @Override
    public Metric updateMetric(Metric value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_metric SET metric_name=:name,metric_unit=:unit,metric_status=:status,
                       current_version_id=:currentVersionId,updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected AND deleted_at IS NULL
                """, metricParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        return value;
    }

    @Override
    public Optional<Metric> findMetric(long tenantId, long metricId) {
        return jdbc.query(metricSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", metricId), this::metric).stream().findFirst();
    }

    @Override
    public List<Metric> listMetrics(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        return jdbc.query(metricSelect() + " AND tenant_id=:tenantId" + filter + " ORDER BY metric_code",
                new MapSqlParameterSource("tenantId", tenantId).addValue("workspaceId", workspaceId), this::metric);
    }

    @Override
    public MetricVersion insertMetricVersion(MetricVersion value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_metric_version
                        (id,tenant_id,metric_id,version_no,dataset_version_id,formula_ast,time_spec_json,
                         currency_spec_json,allowed_dimensions_json,precision_scale,definition_hash,
                         version_status,published_at,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:metricId,:versionNo,:datasetVersionId,:formula,:timeSpec,
                         :currencySpec,:dimensions,:precision,:hash,:status,:publishedAt,NOW(3),NOW(3),:lockVersion)
                    """, metricVersionParameters(value));
            insertDependencies(value);
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("指标版本号已存在");
        }
    }

    @Override
    public MetricVersion updateMetricVersion(MetricVersion value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_metric_version
                   SET dataset_version_id=:datasetVersionId,formula_ast=:formula,time_spec_json=:timeSpec,
                       currency_spec_json=:currencySpec,allowed_dimensions_json=:dimensions,
                       precision_scale=:precision,definition_hash=:hash,version_status=:status,
                       published_at=:publishedAt,updated_at=NOW(3),lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected AND deleted_at IS NULL
                """, metricVersionParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        jdbc.update("DELETE FROM bi_metric_dependency WHERE tenant_id=:tenantId AND metric_version_id=:id",
                Map.of("tenantId", value.tenantId(), "id", value.id()));
        insertDependencies(value);
        return value;
    }

    @Override
    public Optional<MetricVersion> findMetricVersion(long tenantId, long versionId) {
        return jdbc.query(metricVersionSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", versionId), this::metricVersion).stream().findFirst();
    }

    @Override
    public List<MetricVersion> listMetricVersions(long tenantId, long metricId) {
        return jdbc.query(metricVersionSelect() + " AND tenant_id=:tenantId AND metric_id=:metricId"
                        + " ORDER BY version_no",
                Map.of("tenantId", tenantId, "metricId", metricId), this::metricVersion);
    }

    @Override
    public List<MetricVersion> listAllMetricVersions(long tenantId) {
        return jdbc.query(metricVersionSelect() + " AND tenant_id=:tenantId",
                Map.of("tenantId", tenantId), this::metricVersion);
    }

    @Override
    public Term insertTerm(Term value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_term
                        (id,tenant_id,workspace_id,term_code,term_name,definition,target_asset_id,
                         review_status,created_at,updated_at,created_by,lock_version)
                    VALUES
                        (:id,:tenantId,:workspaceId,:code,:name,:definition,:targetAssetId,
                         :status,:createdAt,:updatedAt,:ownerId,:lockVersion)
                    """, termParameters(value));
            insertSynonyms(value);
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("业务术语编码已存在");
        }
    }

    @Override
    public Term updateTerm(Term value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_term SET term_name=:name,definition=:definition,target_asset_id=:targetAssetId,
                       review_status=:status,updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected AND deleted_at IS NULL
                """, termParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        jdbc.update("DELETE FROM bi_term_synonym WHERE tenant_id=:tenantId AND term_id=:id",
                Map.of("tenantId", value.tenantId(), "id", value.id()));
        insertSynonyms(value);
        return value;
    }

    @Override
    public Optional<Term> findTerm(long tenantId, long termId) {
        return jdbc.query(termSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", termId), this::term).stream().findFirst();
    }

    @Override
    public List<Term> listTerms(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        return jdbc.query(termSelect() + " AND tenant_id=:tenantId" + filter + " ORDER BY term_code",
                new MapSqlParameterSource("tenantId", tenantId).addValue("workspaceId", workspaceId), this::term);
    }

    private void insertFields(DatasetVersion version) {
        version.fields().forEach(field -> jdbc.update("""
                INSERT INTO bi_dataset_field
                    (id,tenant_id,dataset_version_id,stable_field_key,field_name,data_type,
                     source_column_id,expression_ast,sensitivity,is_dimension,created_at,updated_at,lock_version)
                VALUES
                    (:id,:tenantId,:versionId,:key,:name,:type,:sourceColumnId,NULL,
                     :sensitivity,:dimension,NOW(3),NOW(3),0)
                ON DUPLICATE KEY UPDATE field_name=VALUES(field_name),data_type=VALUES(data_type),
                    source_column_id=VALUES(source_column_id),sensitivity=VALUES(sensitivity),
                    is_dimension=VALUES(is_dimension),updated_at=NOW(3),lock_version=lock_version+1,
                    deleted_at=NULL
                """, new MapSqlParameterSource().addValue("id", field.id())
                .addValue("tenantId", version.tenantId()).addValue("versionId", version.id())
                .addValue("key", field.stableFieldKey()).addValue("name", field.businessName())
                .addValue("type", field.dataType()).addValue("sourceColumnId", field.sourceColumnId())
                .addValue("sensitivity", field.sensitivity())
                .addValue("dimension", "DIMENSION".equals(field.role()))));
    }

    private void insertRelations(DatasetVersion version) {
        version.relations().forEach(relation -> jdbc.update("""
                INSERT INTO bi_dataset_relation
                    (id,tenant_id,dataset_version_id,related_version_id,relation_code,cardinality,
                     join_ast,created_at,updated_at,lock_version)
                VALUES
                    (:id,:tenantId,:versionId,:relatedVersionId,:code,:cardinality,
                     :joinAst,NOW(3),NOW(3),0)
                ON DUPLICATE KEY UPDATE related_version_id=VALUES(related_version_id),
                    cardinality=VALUES(cardinality),join_ast=VALUES(join_ast),updated_at=NOW(3),
                    lock_version=lock_version+1,deleted_at=NULL
                """, new MapSqlParameterSource().addValue("id", relation.id())
                .addValue("tenantId", version.tenantId()).addValue("versionId", version.id())
                .addValue("relatedVersionId", relation.relatedVersionId()).addValue("code", relation.code())
                .addValue("cardinality", relation.cardinality()).addValue("joinAst", json(relation))));
    }

    private void insertDependencies(MetricVersion version) {
        version.dependencyVersionIds().forEach(dependencyId -> jdbc.update("""
                INSERT INTO bi_metric_dependency
                    (id,tenant_id,metric_version_id,depends_metric_version_id,created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:versionId,:dependencyId,NOW(3),NOW(3),0)
                """, Map.of("id", newId(), "tenantId", version.tenantId(), "versionId", version.id(),
                "dependencyId", dependencyId)));
    }

    private void insertSynonyms(Term term) {
        term.synonyms().forEach(synonym -> jdbc.update("""
                INSERT INTO bi_term_synonym
                    (id,tenant_id,term_id,synonym_text,created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:termId,:text,NOW(3),NOW(3),0)
                """, Map.of("id", newId(), "tenantId", term.tenantId(), "termId", term.id(), "text", synonym)));
    }

    private Dataset dataset(ResultSet result, int row) throws SQLException {
        return new Dataset(result.getLong("id"), result.getLong("tenant_id"), result.getLong("workspace_id"),
                result.getString("dataset_code"), result.getString("dataset_name"),
                result.getString("dataset_status"), nullableLong(result, "current_version_id"),
                result.getLong("owner_member_id"), result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(), result.getLong("lock_version"));
    }

    private DatasetVersion datasetVersion(ResultSet result, int row) throws SQLException {
        try {
            DatasetVersion definition = objectMapper.readValue(result.getString("definition_json"), DatasetVersion.class);
            Timestamp published = result.getTimestamp("published_at");
            return new DatasetVersion(result.getLong("id"), result.getLong("tenant_id"),
                    result.getLong("dataset_id"), result.getInt("version_no"),
                    nullableLong(result, "source_object_id"), definition.fields(), definition.relations(),
                    definition.previewRows(), result.getString("definition_hash"),
                    result.getString("version_status"), published == null ? null : published.toInstant(),
                    result.getLong("lock_version"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("数据集定义无法解析", exception);
        }
    }

    private Metric metric(ResultSet result, int row) throws SQLException {
        return new Metric(result.getLong("id"), result.getLong("tenant_id"), result.getLong("workspace_id"),
                result.getString("metric_code"), result.getString("metric_name"), result.getString("metric_unit"),
                result.getString("metric_status"), nullableLong(result, "current_version_id"),
                result.getLong("owner_member_id"), result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(), result.getLong("lock_version"));
    }

    private MetricVersion metricVersion(ResultSet result, int row) throws SQLException {
        try {
            long id = result.getLong("id");
            long tenantId = result.getLong("tenant_id");
            List<Long> dependencies = jdbc.queryForList("""
                    SELECT depends_metric_version_id FROM bi_metric_dependency
                     WHERE tenant_id=:tenantId AND metric_version_id=:id AND deleted_at IS NULL
                    """, Map.of("tenantId", tenantId, "id", id), Long.class);
            Timestamp published = result.getTimestamp("published_at");
            return new MetricVersion(id, tenantId, result.getLong("metric_id"), result.getInt("version_no"),
                    result.getLong("dataset_version_id"), objectMapper.readTree(result.getString("formula_ast")),
                    objectMapper.readTree(result.getString("time_spec_json")),
                    objectMapper.readTree(result.getString("currency_spec_json")),
                    objectMapper.readerForListOf(String.class).readValue(result.getString("allowed_dimensions_json")),
                    result.getInt("precision_scale"), dependencies, result.getString("definition_hash"),
                    result.getString("version_status"), published == null ? null : published.toInstant(),
                    result.getLong("lock_version"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("指标定义无法解析", exception);
        }
    }

    private Term term(ResultSet result, int row) throws SQLException {
        long tenantId = result.getLong("tenant_id");
        long id = result.getLong("id");
        List<String> synonyms = jdbc.queryForList("""
                SELECT synonym_text FROM bi_term_synonym
                 WHERE tenant_id=:tenantId AND term_id=:id AND deleted_at IS NULL ORDER BY synonym_text
                """, Map.of("tenantId", tenantId, "id", id), String.class);
        Long createdBy = nullableLong(result, "created_by");
        return new Term(id, tenantId, result.getLong("workspace_id"), result.getString("term_code"),
                result.getString("term_name"), result.getString("definition"),
                nullableLong(result, "target_asset_id"), synonyms, result.getString("review_status"),
                createdBy == null ? 0 : createdBy, result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(), result.getLong("lock_version"));
    }

    private MapSqlParameterSource datasetParameters(Dataset value) {
        return new MapSqlParameterSource().addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("workspaceId", value.workspaceId()).addValue("code", value.code())
                .addValue("name", value.name()).addValue("status", value.status())
                .addValue("currentVersionId", value.currentVersionId()).addValue("ownerId", value.ownerMemberId())
                .addValue("createdAt", Timestamp.from(value.createdAt()))
                .addValue("updatedAt", Timestamp.from(value.updatedAt())).addValue("lockVersion", value.lockVersion());
    }

    private MapSqlParameterSource datasetVersionParameters(DatasetVersion value) {
        return new MapSqlParameterSource().addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("datasetId", value.datasetId()).addValue("versionNo", value.versionNo())
                .addValue("sourceObjectId", value.sourceObjectId()).addValue("definition", json(value))
                .addValue("hash", value.definitionHash()).addValue("status", value.status())
                .addValue("publishedAt", timestamp(value.publishedAt())).addValue("lockVersion", value.lockVersion());
    }

    private MapSqlParameterSource metricParameters(Metric value) {
        return new MapSqlParameterSource().addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("workspaceId", value.workspaceId()).addValue("code", value.code())
                .addValue("name", value.name()).addValue("unit", value.unit()).addValue("status", value.status())
                .addValue("currentVersionId", value.currentVersionId()).addValue("ownerId", value.ownerMemberId())
                .addValue("createdAt", Timestamp.from(value.createdAt()))
                .addValue("updatedAt", Timestamp.from(value.updatedAt())).addValue("lockVersion", value.lockVersion());
    }

    private MapSqlParameterSource metricVersionParameters(MetricVersion value) {
        return new MapSqlParameterSource().addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("metricId", value.metricId()).addValue("versionNo", value.versionNo())
                .addValue("datasetVersionId", value.datasetVersionId()).addValue("formula", json(value.formulaAst()))
                .addValue("timeSpec", json(value.timeSpec())).addValue("currencySpec", json(value.currencySpec()))
                .addValue("dimensions", json(value.allowedDimensions())).addValue("precision", value.precisionScale())
                .addValue("hash", value.definitionHash()).addValue("status", value.status())
                .addValue("publishedAt", timestamp(value.publishedAt())).addValue("lockVersion", value.lockVersion());
    }

    private MapSqlParameterSource termParameters(Term value) {
        return new MapSqlParameterSource().addValue("id", value.id()).addValue("tenantId", value.tenantId())
                .addValue("workspaceId", value.workspaceId()).addValue("code", value.code())
                .addValue("name", value.name()).addValue("definition", value.definition())
                .addValue("targetAssetId", value.targetAssetId()).addValue("status", value.status())
                .addValue("ownerId", value.ownerMemberId()).addValue("createdAt", Timestamp.from(value.createdAt()))
                .addValue("updatedAt", Timestamp.from(value.updatedAt())).addValue("lockVersion", value.lockVersion());
    }

    private String datasetSelect() {
        return "SELECT id,tenant_id,workspace_id,dataset_code,dataset_name,dataset_status,"
                + "current_version_id,owner_member_id,created_at,updated_at,lock_version "
                + "FROM bi_dataset WHERE deleted_at IS NULL";
    }

    private String datasetVersionSelect() {
        return "SELECT id,tenant_id,dataset_id,version_no,source_object_id,definition_json,definition_hash,"
                + "version_status,published_at,lock_version FROM bi_dataset_version WHERE deleted_at IS NULL";
    }

    private String metricSelect() {
        return "SELECT id,tenant_id,workspace_id,metric_code,metric_name,metric_unit,metric_status,"
                + "current_version_id,owner_member_id,created_at,updated_at,lock_version "
                + "FROM bi_metric WHERE deleted_at IS NULL";
    }

    private String metricVersionSelect() {
        return "SELECT id,tenant_id,metric_id,version_no,dataset_version_id,formula_ast,time_spec_json,"
                + "currency_spec_json,allowed_dimensions_json,precision_scale,definition_hash,version_status,"
                + "published_at,lock_version FROM bi_metric_version WHERE deleted_at IS NULL";
    }

    private String termSelect() {
        return "SELECT id,tenant_id,workspace_id,term_code,term_name,definition,target_asset_id,review_status,"
                + "created_by,created_at,updated_at,lock_version FROM bi_term WHERE deleted_at IS NULL";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("语义定义 JSON 序列化失败", exception);
        }
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private void optimistic(int changed) {
        if (changed != 1) {
            throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
