package com.aibi.platform.semantic.dto;

import com.aibi.platform.common.json.JsonResourceDocument;
import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** 数据集、指标与业务术语的 API 契约。 */
public final class SemanticDtos {

    private SemanticDtos() {
    }

    public record FieldInput(
            @NotBlank @Size(max = 64) String stableFieldKey,
            @Positive @JsonResourceId Long sourceColumnId,
            @NotBlank @Size(max = 200) String physicalName,
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Size(max = 128) String dataType,
            @NotBlank @Pattern(regexp = "DIMENSION|MEASURE") String role,
            @NotBlank @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED") String sensitivity,
            @Size(max = 128) String displayFormat) {

        public FieldInput(String stableFieldKey, Long sourceColumnId, String physicalName,
                String businessName, String dataType, String role, String sensitivity) {
            this(stableFieldKey, sourceColumnId, physicalName, businessName, dataType,
                    role, sensitivity, null);
        }
    }

    public record RelationInput(
            @NotBlank @Size(max = 64) String code,
            @Positive @JsonResourceId long relatedVersionId,
            @NotBlank @Size(max = 64) String leftFieldKey,
            @NotBlank @Size(max = 64) String rightFieldKey,
            @NotBlank @Pattern(regexp = "ONE_TO_ONE|MANY_TO_ONE|ONE_TO_MANY|MANY_TO_MANY") String cardinality,
            boolean approved,
            boolean rightKeyUnique,
            @Size(max = 64) String deduplicationRule) {
    }

    public record CreateDatasetRequest(
            @Positive @JsonResourceId long workspaceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @Positive @JsonResourceId Long sourceObjectId,
            @NotEmpty @Size(max = 500) List<@Valid FieldInput> fields,
            @Size(max = 100) List<@Valid RelationInput> relations,
            @Size(max = 100) List<Map<String, Object>> previewRows) {
    }

    public record UpdateDatasetRequest(
            @NotBlank @Size(max = 200) String name,
            @Positive @JsonResourceId Long sourceObjectId,
            @NotEmpty @Size(max = 500) List<@Valid FieldInput> fields,
            @Size(max = 100) List<@Valid RelationInput> relations,
            @Size(max = 100) List<Map<String, Object>> previewRows,
            @PositiveOrZero long lockVersion) {
    }

    public record DatasetData(Dataset dataset, DatasetVersion draft) {
    }

    public record ValidationIssue(String code, String path, String message) {
    }

    public record ValidationResult(boolean valid, List<ValidationIssue> errors,
            List<ValidationIssue> warnings) {
    }

    public record PreviewData(@JsonResourceId long datasetId, @JsonResourceId long datasetVersionId,
            List<DatasetField> fields, List<Map<String, Object>> rows,
            int totalBeforePolicy, int visibleAfterPolicy) {
    }

    public record CreateMetricRequest(
            @Positive @JsonResourceId long workspaceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 32) String unit,
            @Positive @JsonResourceId long datasetVersionId,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.METRIC_FORMULA) JsonNode formulaAst,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.TIME_SPEC) JsonNode timeSpec,
            @NotNull JsonNode currencySpec,
            @Size(max = 100) List<@NotBlank String> allowedDimensions,
            @Min(0) @Max(8) int precisionScale,
            @Size(max = 100) @JsonResourceIds List<@Positive Long> dependencyVersionIds) {
    }

    public record UpdateMetricRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 32) String unit,
            @Positive @JsonResourceId long datasetVersionId,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.METRIC_FORMULA) JsonNode formulaAst,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.TIME_SPEC) JsonNode timeSpec,
            @NotNull JsonNode currencySpec,
            @Size(max = 100) List<@NotBlank String> allowedDimensions,
            @Min(0) @Max(8) int precisionScale,
            @Size(max = 100) @JsonResourceIds List<@Positive Long> dependencyVersionIds,
            @PositiveOrZero long lockVersion) {
    }

    public record MetricData(Metric metric, MetricVersion draft) {
    }

    public record CreateTermRequest(
            @Positive @JsonResourceId long workspaceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 4000) String definition,
            @Positive @JsonResourceId Long targetAssetId,
            @Size(max = 100) List<@NotBlank @Size(max = 200) String> synonyms) {
    }

    public record UpdateTermRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 4000) String definition,
            @Positive @JsonResourceId Long targetAssetId,
            @Size(max = 100) List<@NotBlank @Size(max = 200) String> synonyms,
            @PositiveOrZero long lockVersion) {
    }

    public record OrderSample(String status, String currency,
            @NotNull java.math.BigDecimal paidAmount,
            @NotNull java.math.BigDecimal refundedAmount) {
    }

    public record CalculationResult(String status, String value, String currency) {
    }
}
