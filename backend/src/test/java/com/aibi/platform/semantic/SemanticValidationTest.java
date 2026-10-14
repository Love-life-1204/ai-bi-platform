package com.aibi.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.semantic.dto.SemanticDtos.RelationInput;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.manager.MetricExpressionValidator;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void datasetRejectsUnapprovedAndDuplicateDimensionRelations() {
        InMemorySemanticMapper mapper = new InMemorySemanticMapper();
        SemanticDefinitionManager manager = new SemanticDefinitionManager(mapper, objectMapper);
        Dataset target = dataset(10, 1, 20, "customers");
        mapper.insertDataset(target);
        mapper.insertDatasetVersion(new DatasetVersion(101, 1, 10, 1, 1L,
                List.of(field(1001, "customer_id", "DIMENSION")), List.of(), List.of(),
                "a".repeat(64), "PUBLISHED", Instant.now(), 0));
        DatasetVersion draft = new DatasetVersion(201, 1, 11, 1, 2L,
                List.of(field(2001, "customer_id", "DIMENSION"),
                        new DatasetField(2002, "raw", 2L, "raw", "非法字段",
                                "RAW_SQL", "DIMENSION", "INTERNAL")),
                manager.relations(List.of(new RelationInput("customer", 101, "customer_id",
                                "customer_id", "MANY_TO_ONE", false, false, null),
                        new RelationInput("customer", 101, "customer_id", "customer_id",
                                "ONE_TO_ONE", true, true, null))),
                List.of(), "", "DRAFT", null, 0);

        ValidationResult result = manager.validate(1, 20, draft);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(issue -> issue.code())
                .contains("UNSUPPORTED_FIELD_TYPE", "DUPLICATE_RELATION_CODE",
                        "RELATION_NOT_APPROVED", "DUPLICATE_DIMENSION_KEY");
    }

    @Test
    void metricRejectsUnknownFunctionAndAcceptsControlledNetSalesFormula() throws Exception {
        InMemorySemanticMapper mapper = new InMemorySemanticMapper();
        mapper.insertDataset(dataset(10, 1, 20, "orders"));
        mapper.insertDatasetVersion(new DatasetVersion(101, 1, 10, 1, 1L,
                List.of(field(1001, "paid", "MEASURE"), field(1002, "refunded", "MEASURE"),
                        field(1003, "order_time", "DIMENSION"), field(1004, "currency", "DIMENSION")),
                List.of(), List.of(), "a".repeat(64), "PUBLISHED", Instant.now(), 0));
        MetricExpressionValidator validator = new MetricExpressionValidator(mapper);
        MetricVersion invalid = metricVersion(objectMapper.readTree("""
                {"op":"RAW_SQL","value":"select * from sys_user"}
                """));
        MetricVersion valid = metricVersion(objectMapper.readTree("""
                {"op":"SUB","args":[
                  {"op":"SUM","args":[{"op":"FIELD","field":"paid"}]},
                  {"op":"SUM","args":[{"op":"FIELD","field":"refunded"}]}
                ]}
                """));

        assertThat(validator.validate(1, 20, invalid).errors())
                .extracting(issue -> issue.code()).contains("OPERATOR_NOT_ALLOWED");
        assertThat(validator.validate(1, 20, valid).valid()).isTrue();
    }

    @Test
    void metricDependencyGraphRejectsCycle() throws Exception {
        InMemorySemanticMapper mapper = new InMemorySemanticMapper();
        mapper.insertDataset(dataset(10, 1, 20, "orders"));
        mapper.insertDatasetVersion(new DatasetVersion(101, 1, 10, 1, 1L,
                List.of(field(1001, "order_time", "DIMENSION"),
                        field(1002, "currency", "DIMENSION")),
                List.of(), List.of(), "a".repeat(64), "PUBLISHED", Instant.now(), 0));
        MetricVersion first = metricVersion(objectMapper.readTree("""
                {"op":"METRIC","metricVersionId":302}
                """), 301, List.of(302L), "DRAFT");
        MetricVersion second = metricVersion(objectMapper.readTree("""
                {"op":"METRIC","metricVersionId":301}
                """), 302, List.of(301L), "PUBLISHED");
        mapper.insertMetricVersion(second);

        ValidationResult result = new MetricExpressionValidator(mapper).validate(1, 20, first);

        assertThat(result.errors()).extracting(issue -> issue.code()).contains("CIRCULAR_DEPENDENCY");
    }

    private Dataset dataset(long id, long tenantId, long workspaceId, String code) {
        return new Dataset(id, tenantId, workspaceId, code, code, "PUBLISHED", 101L,
                99, Instant.now(), Instant.now(), 0);
    }

    private DatasetField field(long id, String key, String role) {
        return new DatasetField(id, key, id, key, key, "DECIMAL", role, "INTERNAL");
    }

    private MetricVersion metricVersion(com.fasterxml.jackson.databind.JsonNode formula) throws Exception {
        return metricVersion(formula, 301, List.of(), "DRAFT");
    }

    private MetricVersion metricVersion(com.fasterxml.jackson.databind.JsonNode formula,
            long id, List<Long> dependencies, String status) throws Exception {
        return new MetricVersion(id, 1, 30, 1, 101, formula,
                objectMapper.readTree("""
                        {"timezone":"Asia/Shanghai","eventTimeField":"order_time",
                         "startInclusive":true,"endExclusive":true}
                        """),
                objectMapper.readTree("""
                        {"mode":"SINGLE","currencyField":"currency"}
                        """), List.of(), 2, dependencies, "b".repeat(64), status, null, 0);
    }
}
