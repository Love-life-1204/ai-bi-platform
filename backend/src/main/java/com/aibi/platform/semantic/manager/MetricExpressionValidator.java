package com.aibi.platform.semantic.manager;

import com.aibi.platform.common.json.ResourceIds;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationIssue;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MetricExpressionValidator {

    public static final Set<String> ALLOWED_OPERATORS = Set.of(
            "FIELD", "LITERAL", "METRIC", "SUM", "COUNT", "COUNT_DISTINCT", "MIN", "MAX", "AVG",
            "ADD", "SUB", "DIV", "CASE", "DATE_TRUNC", "NULLIF", "EQ", "NE", "GT", "GTE", "LT", "LTE");
    private final SemanticMapper mapper;

    public MetricExpressionValidator(SemanticMapper mapper) {
        this.mapper = mapper;
    }

    public ValidationResult validate(long tenantId, long workspaceId, MetricVersion version) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        DatasetVersion dataset = mapper.findDatasetVersion(tenantId, version.datasetVersionId()).orElse(null);
        if (dataset == null || !"PUBLISHED".equals(dataset.status())) {
            errors.add(issue("DATASET_VERSION_NOT_PUBLISHED", "datasetVersionId",
                    "指标必须引用已发布的数据集版本"));
        } else {
            boolean sameWorkspace = mapper.findDataset(tenantId, dataset.datasetId())
                    .map(value -> value.workspaceId() == workspaceId).orElse(false);
            if (!sameWorkspace) {
                errors.add(issue("DATASET_WORKSPACE_MISMATCH", "datasetVersionId",
                        "指标与数据集必须属于同一工作空间"));
            }
            dataset.fields().forEach(field -> fields.add(field.stableFieldKey()));
            validateNode(version.formulaAst(), "formulaAst", fields, errors);
            Set<String> dimensions = dataset.fields().stream().filter(field -> "DIMENSION".equals(field.role()))
                    .map(DatasetField::stableFieldKey).collect(java.util.stream.Collectors.toSet());
            for (String dimension : version.allowedDimensions()) {
                if (!dimensions.contains(dimension)) {
                    errors.add(issue("DIMENSION_NOT_FOUND", "allowedDimensions", "允许的维度不存在: " + dimension));
                }
            }
        }
        validateTime(version.timeSpec(), fields, errors);
        validateCurrency(version.currencySpec(), fields, errors);
        Set<Long> formulaDependencies = new HashSet<>();
        collectMetricReferences(version.formulaAst(), formulaDependencies);
        if (!formulaDependencies.equals(new HashSet<>(version.dependencyVersionIds()))) {
            errors.add(issue("DEPENDENCY_DECLARATION_MISMATCH", "dependencyVersionIds",
                    "公式中的指标版本引用必须与显式依赖列表完全一致"));
        }
        validateDependencies(tenantId, version, errors);
        if (containsDivision(version.formulaAst())) {
            warnings.add(issue("ZERO_DENOMINATOR_RETURNS_UNDEFINED", "formulaAst",
                    "除数为零时结果返回 UNDEFINED，而不是 0 或无穷大"));
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    private void validateNode(JsonNode node, String path, Set<String> fields, List<ValidationIssue> errors) {
        if (node == null || !node.isObject()) {
            errors.add(issue("INVALID_AST_NODE", path, "公式节点必须是 JSON 对象"));
            return;
        }
        String operator = node.path("op").asText("").toUpperCase();
        if (!ALLOWED_OPERATORS.contains(operator)) {
            errors.add(issue("OPERATOR_NOT_ALLOWED", path + ".op", "公式函数不在白名单中: " + operator));
            return;
        }
        if ("FIELD".equals(operator) && !fields.contains(node.path("field").asText())) {
            errors.add(issue("FIELD_NOT_FOUND", path + ".field", "公式引用的字段不存在"));
        }
        if ("METRIC".equals(operator) && !ResourceIds.valid(node.path("metricVersionId"))) {
            errors.add(issue("METRIC_REFERENCE_REQUIRED", path + ".metricVersionId", "指标引用缺少版本 ID"));
        }
        JsonNode args = node.path("args");
        if (Set.of("SUM", "COUNT_DISTINCT", "MIN", "MAX", "AVG").contains(operator)
                && (!args.isArray() || args.size() != 1)) {
            errors.add(issue("INVALID_ARGUMENT_COUNT", path + ".args", operator + " 必须有一个参数"));
        }
        if (Set.of("ADD", "SUB", "DIV", "NULLIF", "EQ", "NE", "GT", "GTE", "LT", "LTE")
                .contains(operator) && (!args.isArray() || args.size() != 2)) {
            errors.add(issue("INVALID_ARGUMENT_COUNT", path + ".args", operator + " 必须有两个参数"));
        }
        if ("CASE".equals(operator) && (!args.isArray() || args.size() != 3)) {
            errors.add(issue("INVALID_ARGUMENT_COUNT", path + ".args", "CASE 必须依次包含条件、成立值和否则值"));
        }
        if (args.isArray()) {
            for (int index = 0; index < args.size(); index++) {
                validateNode(args.get(index), path + ".args[" + index + "]", fields, errors);
            }
        }
    }

    private void validateTime(JsonNode spec, Set<String> fields, List<ValidationIssue> errors) {
        String timezone = spec.path("timezone").asText("");
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            errors.add(issue("INVALID_TIMEZONE", "timeSpec.timezone", "时区必须是有效的 IANA 时区"));
        }
        if (!spec.path("startInclusive").asBoolean(false) || !spec.path("endExclusive").asBoolean(false)) {
            errors.add(issue("INVALID_TIME_RANGE", "timeSpec", "时间范围必须使用 [start, end) 规则"));
        }
        String eventTimeField = spec.path("eventTimeField").asText("");
        if (eventTimeField.isBlank()) {
            errors.add(issue("EVENT_TIME_REQUIRED", "timeSpec.eventTimeField", "必须指定业务时间字段"));
        } else if (!fields.isEmpty() && !fields.contains(eventTimeField)) {
            errors.add(issue("EVENT_TIME_FIELD_NOT_FOUND", "timeSpec.eventTimeField", "业务时间字段不存在"));
        }
    }

    private void validateCurrency(JsonNode spec, Set<String> fields, List<ValidationIssue> errors) {
        String mode = spec.path("mode").asText("").toUpperCase();
        if (!Set.of("SINGLE", "FIXED", "CONVERT").contains(mode)) {
            errors.add(issue("INVALID_CURRENCY_MODE", "currencySpec.mode", "币种规则必须为 SINGLE、FIXED 或 CONVERT"));
            return;
        }
        String currencyField = spec.path("currencyField").asText("");
        if (currencyField.isBlank()) {
            errors.add(issue("CURRENCY_FIELD_REQUIRED", "currencySpec.currencyField", "必须指定币种字段"));
        } else if (!fields.isEmpty() && !fields.contains(currencyField)) {
            errors.add(issue("CURRENCY_FIELD_NOT_FOUND", "currencySpec.currencyField", "币种字段不存在"));
        }
        if ("CONVERT".equals(mode) && (spec.path("targetCurrency").asText("").isBlank()
                || spec.path("rateSource").asText("").isBlank())) {
            errors.add(issue("EXCHANGE_RATE_REQUIRED", "currencySpec",
                    "跨币种汇总必须指定目标币种和已批准汇率来源"));
        }
    }

    private void validateDependencies(long tenantId, MetricVersion candidate, List<ValidationIssue> errors) {
        Map<Long, MetricVersion> versions = new HashMap<>();
        mapper.listAllMetricVersions(tenantId).forEach(value -> versions.put(value.id(), value));
        versions.put(candidate.id(), candidate);
        for (Long dependencyId : candidate.dependencyVersionIds()) {
            MetricVersion dependency = versions.get(dependencyId);
            if (dependency == null || !"PUBLISHED".equals(dependency.status())) {
                errors.add(issue("DEPENDENCY_NOT_PUBLISHED", "dependencyVersionIds",
                        "依赖指标必须是同租户的已发布版本: " + dependencyId));
            }
        }
        if (hasCycle(candidate.id(), candidate.id(), versions, new HashSet<>())) {
            errors.add(issue("CIRCULAR_DEPENDENCY", "dependencyVersionIds", "指标依赖存在循环"));
        }
    }

    private boolean hasCycle(long origin, long current, Map<Long, MetricVersion> versions, Set<Long> visited) {
        MetricVersion value = versions.get(current);
        if (value == null || !visited.add(current)) {
            return false;
        }
        for (Long next : value.dependencyVersionIds()) {
            if (next == origin || hasCycle(origin, next, versions, new HashSet<>(visited))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDivision(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if ("DIV".equalsIgnoreCase(node.path("op").asText())) {
            return true;
        }
        for (JsonNode child : node.path("args")) {
            if (containsDivision(child)) {
                return true;
            }
        }
        return false;
    }

    private void collectMetricReferences(JsonNode node, Set<Long> references) {
        if (node == null || !node.isObject()) {
            return;
        }
        if ("METRIC".equalsIgnoreCase(node.path("op").asText())
                && ResourceIds.valid(node.path("metricVersionId"))) {
            references.add(ResourceIds.parse(node.path("metricVersionId"))); 
        }
        for (JsonNode child : node.path("args")) {
            collectMetricReferences(child, references);
        }
    }

    private ValidationIssue issue(String code, String path, String message) {
        return new ValidationIssue(code, path, message);
    }
}
