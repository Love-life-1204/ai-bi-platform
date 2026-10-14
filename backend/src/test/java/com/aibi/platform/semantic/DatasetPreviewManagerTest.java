package com.aibi.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.identity.dto.ColumnAccessData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.semantic.dto.SemanticDtos.PreviewData;
import com.aibi.platform.semantic.manager.DatasetPreviewManager;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetPreviewManagerTest {

    @Test
    void rowPoliciesFilterBeforeColumnDenyAndMask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<DatasetField> fields = List.of(
                field(1, "region"), field(2, "customer_name"), field(3, "mobile"));
        DatasetVersion version = new DatasetVersion(20, 1, 10, 1, null, fields, List.of(), List.of(
                Map.of("region", "EAST", "customer_name", "张三", "mobile", "13800000000"),
                Map.of("region", "WEST", "customer_name", "李四", "mobile", "13900000000")),
                "a".repeat(64), "PUBLISHED", null, 0);
        JsonNode eastOnly = objectMapper.readTree("""
                {"op":"EQ","field":"region","value":"EAST"}
                """);
        DataPolicyEvaluationData policy = new DataPolicyEvaluationData(10,
                Map.of("region_scope", List.of(eastOnly)), List.of(
                new ColumnAccessData(1, "VISIBLE", null),
                new ColumnAccessData(2, "DENY", null),
                new ColumnAccessData(3, "MASKED", null)));

        PreviewData result = new DatasetPreviewManager().apply(10, version, policy);

        assertThat(result.totalBeforePolicy()).isEqualTo(2);
        assertThat(result.visibleAfterPolicy()).isEqualTo(1);
        assertThat(result.fields()).extracting(DatasetField::stableFieldKey)
                .containsExactly("region", "mobile");
        assertThat(result.rows().get(0)).containsEntry("region", "EAST")
                .containsEntry("mobile", "1***0").doesNotContainKey("customer_name");
    }

    private DatasetField field(long id, String key) {
        return new DatasetField(id, key, id, key, key, "STRING", "DIMENSION", "INTERNAL");
    }
}
