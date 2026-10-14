package com.aibi.platform.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibi.platform.dashboard.dto.DashboardDtos.CreateChartRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateMetricRequest;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceDocumentContractRepairTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void declaredIdsInFormulaAndTimeSpecMustBeStringsButLiteralValuesStayNumeric() throws Exception {
        var formula = json.readTree("""
                {"op":"ADD","args":[{"op":"METRIC","metricVersionId":9007199254740993},
                {"op":"LITERAL","value":9007199254740993,"metricVersionId":7}]}
                """);
        var model = new MetricVersion(1, 1, 1, 1, 1, formula,
                json.readTree("{\"fieldId\":9223372036854775807}"), json.createObjectNode(),
                List.of(), 2, List.of(9007199254740993L), "hash", "DRAFT", null, 0);
        var wire = json.readTree(json.writeValueAsBytes(model));
        assertEquals("9007199254740993", wire.path("formulaAst").path("args").get(0)
                .path("metricVersionId").textValue());
        assertEquals("9223372036854775807", wire.path("timeSpec").path("fieldId").textValue());
        assertTrue(wire.path("formulaAst").path("args").get(1).path("value").isIntegralNumber());
        assertTrue(wire.path("formulaAst").path("args").get(1).path("metricVersionId").isIntegralNumber());
        assertTrue(formula.path("args").get(0).path("metricVersionId").isIntegralNumber());
    }

    @Test
    void legacyIntegerDocumentIdsAreNormalizedWhenBindingRequests() throws Exception {
        var request = json.readValue(metricBody("9007199254740993"), CreateMetricRequest.class);
        assertEquals("9007199254740993", request.formulaAst().path("metricVersionId").textValue());
        assertEquals("9007199254740993", request.timeSpec().path("fieldId").textValue());
    }

    @Test
    void floatOrNonCanonicalIdsInsideRawDocumentsAreRejected() {
        for (String token : List.of("1.0", "1e3", "\"01\"", "\" 1\"", "9223372036854775808")) {
            assertThrows(JsonProcessingException.class,
                    () -> json.readValue(metricBody(token), CreateMetricRequest.class));
        }
    }

    @Test
    void chartInteractionIdsAreNormalizedWithoutChangingDisplayOptions() throws Exception {
        var request = json.readValue("""
                {"workspaceId":"1","code":"chart_exact","name":"c","datasetVersionId":"1",
                "queryPlan":{"datasetVersionId":"1","metricVersionIds":["1"],"limit":5},
                "chartSpec":{"interactionFieldId":9007199254740993,"decimalPlaces":2}}
                """, CreateChartRequest.class);
        assertEquals("9007199254740993", request.chartSpec().path("interactionFieldId").textValue());
        assertEquals(2, request.chartSpec().path("decimalPlaces").intValue());
    }

    private String metricBody(String token) {
        return """
                {"workspaceId":"1","code":"metric_exact","name":"m","unit":"CNY",
                "datasetVersionId":"1","formulaAst":{"op":"METRIC","metricVersionId":%s},
                "timeSpec":{"fieldId":%s},"currencySpec":{},"precisionScale":2}
                """.formatted(token, token);
    }
}
