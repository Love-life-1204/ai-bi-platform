package com.aibi.platform.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Copies only declared ID positions. Query rows, literal values and arbitrary keys are untouched. */
public final class ResourceDocuments {
    private ResourceDocuments() {
    }

    public static JsonNode normalize(JsonNode source, JsonResourceDocument.Kind kind) {
        JsonNode copy = source.deepCopy();
        switch (kind) {
            case METRIC_FORMULA -> formula(copy);
            case TIME_SPEC -> field(copy, "fieldId");
            case CHART_SPEC -> field(copy, "interactionFieldId");
            case DASHBOARD_FILTERS -> {
                if (copy.isArray()) {
                    copy.forEach(filter -> field(filter, "fieldId"));
                }
            }
            default -> throw new IllegalArgumentException("未声明的资源文档契约");
        }
        return copy;
    }

    private static void formula(JsonNode node) {
        if ("METRIC".equalsIgnoreCase(node.path("op").asText())) {
            field(node, "metricVersionId");
        }
        if (node.path("args").isArray()) {
            node.path("args").forEach(ResourceDocuments::formula);
        }
    }

    private static void field(JsonNode node, String name) {
        if (node instanceof ObjectNode object && object.has(name)) {
            object.put(name, Long.toString(ResourceIds.parse(object.get(name))));
        }
    }
}
