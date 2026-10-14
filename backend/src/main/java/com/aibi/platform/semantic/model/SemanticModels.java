package com.aibi.platform.semantic.model;

import com.aibi.platform.common.json.JsonResourceDocument;
import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 语义层持久化模型。发布版本一经生成即不再原地修改。 */
public final class SemanticModels {

    private SemanticModels() {
    }

    public record Dataset(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String code, String name,
            String status, @JsonResourceId Long currentVersionId, @JsonResourceId long ownerMemberId,
            Instant createdAt, Instant updatedAt, long lockVersion) {
    }

    public record DatasetField(
            @JsonResourceId long id, String stableFieldKey, @JsonResourceId Long sourceColumnId, String physicalName,
            String businessName, String dataType, String role, String sensitivity,
            String displayFormat) {

        public DatasetField(long id, String stableFieldKey, Long sourceColumnId,
                String physicalName, String businessName, String dataType,
                String role, String sensitivity) {
            this(id, stableFieldKey, sourceColumnId, physicalName, businessName,
                    dataType, role, sensitivity, null);
        }
    }

    public record DatasetRelation(
            @JsonResourceId long id, String code, @JsonResourceId long relatedVersionId, String leftFieldKey,
            String rightFieldKey, String cardinality, boolean approved,
            boolean rightKeyUnique, String deduplicationRule) {
    }

    public record DatasetVersion(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long datasetId, int versionNo, @JsonResourceId Long sourceObjectId,
            List<DatasetField> fields, List<DatasetRelation> relations,
            List<Map<String, Object>> previewRows, String definitionHash,
            String status, Instant publishedAt, long lockVersion) {
    }

    public record Metric(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String code, String name,
            String unit, String status, @JsonResourceId Long currentVersionId, @JsonResourceId long ownerMemberId,
            Instant createdAt, Instant updatedAt, long lockVersion) {
    }

    public record MetricVersion(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long metricId, int versionNo, @JsonResourceId long datasetVersionId,
            @JsonResourceDocument(JsonResourceDocument.Kind.METRIC_FORMULA) JsonNode formulaAst, @JsonResourceDocument(JsonResourceDocument.Kind.TIME_SPEC) JsonNode timeSpec, JsonNode currencySpec,
            List<String> allowedDimensions, int precisionScale,
            @JsonResourceIds List<Long> dependencyVersionIds, String definitionHash,
            String status, Instant publishedAt, long lockVersion) {
    }

    public record Term(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String code, String name,
            String definition, @JsonResourceId Long targetAssetId, List<String> synonyms,
            String status, @JsonResourceId long ownerMemberId, Instant createdAt, Instant updatedAt,
            long lockVersion) {
    }
}
