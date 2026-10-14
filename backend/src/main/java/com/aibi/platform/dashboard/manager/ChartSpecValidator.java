package com.aibi.platform.dashboard.manager;

import com.aibi.platform.common.json.ResourceIds;
import com.aibi.platform.dashboard.dto.DashboardDtos.ChartValidationData;
import com.aibi.platform.dashboard.dto.DashboardDtos.ValidationIssue;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates the safe, declarative subset that is converted to ECharts options in the browser. */
@Component
public class ChartSpecValidator {

    private static final Set<String> TYPES = Set.of(
            "KPI", "BAR", "LINE", "PIE", "STACKED_BAR", "TABLE", "PIVOT_TABLE");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "type", "title", "categoryField", "valueFields", "seriesNameField",
            "valueFormat", "legend", "labels", "emptyText", "drill", "detailColumns",
            "interactionFieldId", "xAxisTitle", "yAxisTitle", "unit", "decimalPlaces");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "formatter", "renderitem", "graphic", "html", "script", "onclick", "tooltiphtml");

    public ChartValidationData validate(JsonNode spec, QueryPlan plan, long datasetVersionId) {
        List<ValidationIssue> errors = new ArrayList<>();
        if (plan.datasetVersionId() != datasetVersionId) {
            error(errors, "queryPlan.datasetVersionId", "DATASET_VERSION_MISMATCH",
                    "查询计划必须引用图表固定的数据集版本");
        }
        if (spec == null || !spec.isObject()) {
            error(errors, "chartSpec", "OBJECT_REQUIRED", "图表配置必须是 JSON 对象");
            return new ChartValidationData(false, List.copyOf(errors));
        }
        spec.fieldNames().forEachRemaining(field -> {
            if (!ROOT_FIELDS.contains(field)) {
                error(errors, "chartSpec." + field, "UNSUPPORTED_OPTION", "不支持的图表配置项");
            }
        });
        rejectUnsafeKeys(spec, "chartSpec", errors);
        String type = spec.path("type").asText("").toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            error(errors, "chartSpec.type", "UNSUPPORTED_CHART_TYPE",
                    "图表类型仅支持 KPI、BAR、LINE、PIE、STACKED_BAR、TABLE、PIVOT_TABLE");
            return new ChartValidationData(false, List.copyOf(errors));
        }
        List<String> values = textArray(spec.path("valueFields"), "chartSpec.valueFields", errors);
        String category = spec.path("categoryField").asText("");
        switch (type) {
            case "KPI" -> requireCount(values, 1, 1, errors, "指标卡必须且只能配置一个数值字段");
            case "PIE" -> {
                requireText(category, errors, "饼图必须配置分类字段");
                requireCount(values, 1, 1, errors, "饼图必须且只能配置一个数值字段");
            }
            case "BAR", "LINE", "STACKED_BAR" -> {
                requireText(category, errors, "坐标图必须配置分类字段");
                requireCount(values, 1, 20, errors, "坐标图至少配置一个数值字段");
            }
            case "TABLE" -> {
                JsonNode columns = spec.path("detailColumns");
                if (!columns.isArray() || columns.size() == 0) {
                    error(errors, "chartSpec.detailColumns", "COLUMNS_REQUIRED",
                            "明细表至少配置一列");
                }
            }
            case "PIVOT_TABLE" -> {
                requireText(category, "chartSpec.categoryField", "CATEGORY_REQUIRED",
                        errors, "透视表必须配置行维度");
                requireText(spec.path("seriesNameField").asText(""),
                        "chartSpec.seriesNameField", "SERIES_REQUIRED",
                        errors, "透视表必须配置列维度");
                requireCount(values, 1, 1, errors, "透视表必须且只能配置一个数值字段");
            }
            default -> {
                // Type membership was checked above.
            }
        }
        Set<String> duplicates = new HashSet<>();
        for (String value : values) {
            if (!duplicates.add(value)) {
                error(errors, "chartSpec.valueFields", "DUPLICATE_FIELD", "数值字段不能重复");
            }
        }
        validateBindings(spec, plan, category, values, errors);
        if ("PIVOT_TABLE".equals(type)) {
            String series = spec.path("seriesNameField").asText("");
            Set<String> dimensions = plan.dimensionFieldIds().stream()
                    .map(id -> "dimension_" + id).collect(java.util.stream.Collectors.toSet());
            if (!dimensions.contains(series)) {
                error(errors, "chartSpec.seriesNameField", "DIMENSION_BINDING_INVALID",
                        "透视表列字段必须来自查询计划的维度输出");
            }
        }
        validateDrill(spec.path("drill"), errors);
        validateDisplayOptions(spec, errors);
        if (spec.has("interactionFieldId") && !ResourceIds.valid(spec.path("interactionFieldId"))) {
            error(errors, "chartSpec.interactionFieldId", "INVALID_FIELD_ID",
                    "联动字段 ID 必须为正整数");
        }
        return new ChartValidationData(errors.isEmpty(), List.copyOf(errors));
    }

    private void validateBindings(JsonNode spec, QueryPlan plan, String category,
            List<String> values, List<ValidationIssue> errors) {
        Set<String> dimensions = plan.dimensionFieldIds().stream()
                .map(id -> "dimension_" + id).collect(java.util.stream.Collectors.toSet());
        Set<String> metrics = plan.metricVersionIds().stream()
                .map(id -> "metric_" + id).collect(java.util.stream.Collectors.toSet());
        if (!category.isBlank() && !dimensions.contains(category)) {
            error(errors, "chartSpec.categoryField", "DIMENSION_BINDING_INVALID",
                    "分类字段必须来自查询计划的维度输出");
        }
        for (String value : values) {
            if (!metrics.contains(value)) {
                error(errors, "chartSpec.valueFields", "METRIC_BINDING_INVALID",
                        "数值字段必须来自查询计划的指标输出");
            }
        }
        JsonNode detailColumns = spec.path("detailColumns");
        if (detailColumns.isArray()) {
            detailColumns.forEach(column -> {
                String key = column.asText("");
                if (!dimensions.contains(key) && !metrics.contains(key)) {
                    error(errors, "chartSpec.detailColumns", "COLUMN_BINDING_INVALID",
                            "明细列必须来自查询计划输出");
                }
            });
        }
        JsonNode drill = spec.path("drill");
        if (drill.path("enabled").asBoolean(false)
                && !dimensions.contains(drill.path("dimensionField").asText())) {
            error(errors, "chartSpec.drill.dimensionField", "DIMENSION_BINDING_INVALID",
                    "下钻字段必须来自查询计划的维度输出");
        }
        if (spec.has("interactionFieldId")
                && (!ResourceIds.valid(spec.path("interactionFieldId"))
                || !plan.dimensionFieldIds().contains(ResourceIds.parse(spec.path("interactionFieldId"))))) {
            error(errors, "chartSpec.interactionFieldId", "DIMENSION_BINDING_INVALID",
                    "联动字段 ID 必须来自查询计划维度");
        }
    }

    private void validateDrill(JsonNode drill, List<ValidationIssue> errors) {
        if (drill.isMissingNode()) {
            return;
        }
        if (!drill.isObject()) {
            error(errors, "chartSpec.drill", "OBJECT_REQUIRED", "下钻配置必须是对象");
            return;
        }
        Set<String> fields = Set.of("enabled", "target", "dimensionField");
        drill.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) {
                error(errors, "chartSpec.drill." + field, "UNSUPPORTED_OPTION",
                        "不支持的下钻配置项");
            }
        });
        if (drill.path("enabled").asBoolean(false)
                && (!"DETAIL".equals(drill.path("target").asText())
                || drill.path("dimensionField").asText("").isBlank())) {
            error(errors, "chartSpec.drill", "INVALID_DRILL",
                    "下钻仅支持 DETAIL 目标并必须声明维度字段");
        }
    }

    private void validateDisplayOptions(JsonNode spec, List<ValidationIssue> errors) {
        JsonNode decimals = spec.path("decimalPlaces");
        if (!decimals.isMissingNode()
                && (!decimals.canConvertToInt() || decimals.asInt() < 0 || decimals.asInt() > 8)) {
            error(errors, "chartSpec.decimalPlaces", "INVALID_DECIMAL_PLACES",
                    "小数位必须是 0 到 8 之间的整数");
        }
        for (String field : List.of("title", "xAxisTitle", "yAxisTitle", "unit", "emptyText")) {
            if (spec.path(field).isTextual() && spec.path(field).asText().length() > 200) {
                error(errors, "chartSpec." + field, "TEXT_TOO_LONG",
                        "显示文本不能超过 200 个字符");
            }
        }
    }

    private List<String> textArray(JsonNode node, String path, List<ValidationIssue> errors) {
        if (!node.isArray()) {
            error(errors, path, "ARRAY_REQUIRED", "数值字段必须是数组");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            String value = node.path(index).asText("").trim();
            if (value.isEmpty() || value.length() > 128) {
                error(errors, path + "[" + index + "]", "INVALID_FIELD_KEY", "字段键不合法");
            } else {
                values.add(value);
            }
        }
        return values;
    }

    private void rejectUnsafeKeys(JsonNode node, String path, List<ValidationIssue> errors) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String normalized = entry.getKey().toLowerCase(Locale.ROOT);
                if (FORBIDDEN_KEYS.contains(normalized) || normalized.startsWith("on")) {
                    error(errors, path + "." + entry.getKey(), "UNSAFE_OPTION",
                            "图表配置不允许脚本、HTML 或回调函数");
                }
                rejectUnsafeKeys(entry.getValue(), path + "." + entry.getKey(), errors);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                rejectUnsafeKeys(node.path(index), path + "[" + index + "]", errors);
            }
        }
    }

    private void requireText(String value, List<ValidationIssue> errors, String message) {
        requireText(value, "chartSpec.categoryField", "CATEGORY_REQUIRED", errors, message);
    }

    private void requireText(String value, String path, String code,
            List<ValidationIssue> errors, String message) {
        if (value == null || value.isBlank()) {
            error(errors, path, code, message);
        }
    }

    private void requireCount(List<String> values, int min, int max,
            List<ValidationIssue> errors, String message) {
        if (values.size() < min || values.size() > max) {
            error(errors, "chartSpec.valueFields", "INVALID_VALUE_FIELD_COUNT", message);
        }
    }

    private void error(List<ValidationIssue> errors, String path, String code, String message) {
        errors.add(new ValidationIssue(path, code, message));
    }
}
