package com.aibi.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.dashboard.manager.ChartSpecValidator;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChartSpecValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChartSpecValidator validator = new ChartSpecValidator();

    @Test
    void acceptsAllSupportedChartFamilies() throws Exception {
        QueryPlan plan = new QueryPlan(10, List.of(20L), List.of(30L),
                null, List.of(), List.of(), 100);
        for (String type : List.of("BAR", "LINE", "PIE", "STACKED_BAR")) {
            var spec = objectMapper.readTree("""
                    {"type":"%s","categoryField":"dimension_30","valueFields":["metric_20"]}
                    """.formatted(type));
            assertThat(validator.validate(spec, plan, 10).valid()).as(type).isTrue();
        }
        assertThat(validator.validate(objectMapper.readTree(
                "{\"type\":\"KPI\",\"valueFields\":[\"metric_20\"]}"), plan, 10).valid())
                .isTrue();
        assertThat(validator.validate(objectMapper.readTree(
                "{\"type\":\"TABLE\",\"valueFields\":[],\"detailColumns\":[\"dimension_30\"]}"),
                plan, 10).valid()).isTrue();
        QueryPlan pivotPlan = new QueryPlan(10, List.of(20L), List.of(30L, 31L),
                null, List.of(), List.of(), 100);
        assertThat(validator.validate(objectMapper.readTree("""
                {"type":"PIVOT_TABLE","categoryField":"dimension_30",
                 "seriesNameField":"dimension_31","valueFields":["metric_20"]}
                """), pivotPlan, 10).valid()).isTrue();
    }

    @Test
    void rejectsExecutableEchartsOptionsAndDatasetMismatch() throws Exception {
        QueryPlan plan = new QueryPlan(11, List.of(20L), List.of(),
                null, List.of(), List.of(), 100);
        var result = validator.validate(objectMapper.readTree("""
                {"type":"KPI","valueFields":["metric_20"],"formatter":"function(){return 1}"}
                """), plan, 10);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(value -> value.code())
                .contains("DATASET_VERSION_MISMATCH", "UNSUPPORTED_OPTION", "UNSAFE_OPTION");
    }

    @Test
    void rejectsInvalidPivotBindingAndDisplayPrecision() throws Exception {
        QueryPlan plan = new QueryPlan(10, List.of(20L), List.of(30L),
                null, List.of(), List.of(), 100);
        var result = validator.validate(objectMapper.readTree("""
                {"type":"PIVOT_TABLE","categoryField":"dimension_30",
                 "seriesNameField":"dimension_31","valueFields":["metric_20"],
                 "decimalPlaces":9}
                """), plan, 10);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(value -> value.code())
                .contains("DIMENSION_BINDING_INVALID", "INVALID_DECIMAL_PLACES");
    }
}
