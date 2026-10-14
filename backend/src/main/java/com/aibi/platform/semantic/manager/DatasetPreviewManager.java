package com.aibi.platform.semantic.manager;

import com.aibi.platform.identity.dto.ColumnAccessData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.semantic.dto.SemanticDtos.PreviewData;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DatasetPreviewManager {

    public PreviewData apply(long datasetId, DatasetVersion version, DataPolicyEvaluationData policy) {
        Map<Long, ColumnAccessData> columns = policy.columns().stream()
                .collect(Collectors.toMap(ColumnAccessData::datasetFieldId, Function.identity()));
        List<DatasetField> visibleFields = version.fields().stream()
                .filter(field -> !"DENY".equals(columns.get(field.id()).accessMode())).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> source : version.previewRows()) {
            if (!matchesAllGroups(source, policy.rowPolicyGroups())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (DatasetField field : visibleFields) {
                ColumnAccessData access = columns.get(field.id());
                Object value = source.get(field.stableFieldKey());
                row.put(field.stableFieldKey(), "MASKED".equals(access.accessMode()) ? mask(value) : value);
            }
            rows.add(row);
        }
        return new PreviewData(datasetId, version.id(), visibleFields, List.copyOf(rows),
                version.previewRows().size(), rows.size());
    }

    private boolean matchesAllGroups(Map<String, Object> row, Map<String, List<JsonNode>> groups) {
        return groups.values().stream().allMatch(group -> group.stream().anyMatch(ast -> matches(row, ast)));
    }

    private boolean matches(Map<String, Object> row, JsonNode node) {
        String op = node.path("op").asText("").toUpperCase();
        return switch (op) {
            case "FALSE" -> false;
            case "TRUE" -> true;
            case "AND" -> allChildren(row, node.path("args"));
            case "OR" -> anyChild(row, node.path("args"));
            case "EQ" -> equalsValue(row.get(node.path("field").asText()), scalar(node.get("value")));
            case "NE" -> !equalsValue(row.get(node.path("field").asText()), scalar(node.get("value")));
            case "IN" -> contains(node.path("values"), row.get(node.path("field").asText()));
            default -> false;
        };
    }

    private boolean allChildren(Map<String, Object> row, JsonNode nodes) {
        for (JsonNode node : nodes) {
            if (!matches(row, node)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyChild(Map<String, Object> row, JsonNode nodes) {
        for (JsonNode node : nodes) {
            if (matches(row, node)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(JsonNode values, Object candidate) {
        for (JsonNode value : values) {
            if (equalsValue(scalar(value), candidate)) {
                return true;
            }
        }
        return false;
    }

    private Object scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private boolean equalsValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return new java.math.BigDecimal(left.toString()).compareTo(
                    new java.math.BigDecimal(right.toString())) == 0;
        }
        return Objects.equals(left, right);
    }

    private Object mask(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() <= 2) {
            return "**";
        }
        return text.substring(0, 1) + "***" + text.substring(text.length() - 1);
    }
}
