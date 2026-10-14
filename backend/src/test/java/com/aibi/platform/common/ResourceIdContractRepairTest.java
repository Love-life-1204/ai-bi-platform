package com.aibi.platform.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibi.platform.common.dto.TenantData;
import com.aibi.platform.identity.dto.SwitchTenantRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.BatchData;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceIdContractRepairTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final long LARGE_ID = 9007199254740993L;

    @Test
    void scalarIdsMustBeStringsWithoutChangingVersionsOrRowCounts() throws Exception {
        JsonNode tenant = json.readTree(json.writeValueAsBytes(new TenantData(LARGE_ID,
                "id-contract", "ID contract", "ACTIVE", "Asia/Shanghai", "CNY", 3,
                Instant.EPOCH, Instant.EPOCH, 4)));
        assertTrue(tenant.path("id").isTextual());
        assertEquals("9007199254740993", tenant.path("id").textValue());
        assertTrue(tenant.path("permissionVersion").isIntegralNumber());
        assertEquals(4, tenant.path("lockVersion").longValue());
        JsonNode batch = json.readTree(json.writeValueAsBytes(new BatchData(LARGE_ID,
                Long.MAX_VALUE, 12, "PUBLISHED", 5, 5, 5, 0, 0, "1.00", "1.00",
                5, 5, "PASSED", null, Instant.EPOCH)));
        assertEquals(Long.toString(Long.MAX_VALUE), batch.path("generationId").textValue());
        assertTrue(batch.path("validRows").isIntegralNumber());
        assertTrue(batch.path("publishSeq").isIntegralNumber());
    }

    @Test
    void nestedModelsAndIdCollectionsMustRetainExactDigits() throws Exception {
        Dataset model = new Dataset(LARGE_ID, Long.MAX_VALUE, LARGE_ID, "d", "d",
                "ACTIVE", LARGE_ID, LARGE_ID, Instant.EPOCH, Instant.EPOCH, 0);
        JsonNode node = json.readTree(json.writeValueAsBytes(model));
        assertEquals(Long.toString(Long.MAX_VALUE), node.path("tenantId").textValue());
        QueryPlan plan = new QueryPlan(LARGE_ID, List.of(LARGE_ID, Long.MAX_VALUE),
                List.of(LARGE_ID), null, List.of(), List.of(), 500);
        JsonNode wire = json.readTree(json.writeValueAsBytes(plan));
        assertEquals("9007199254740993", wire.path("metricVersionIds").get(0).textValue());
        assertEquals(Long.toString(Long.MAX_VALUE), wire.path("metricVersionIds").get(1).textValue());
        assertEquals(plan, json.readValue(json.writeValueAsBytes(plan), QueryPlan.class));
    }

    @Test
    void canonicalStringsAndLegacyIntegerTokensMustParseExactly() throws Exception {
        for (long id : new long[]{9007199254740991L, 9007199254740992L, LARGE_ID, Long.MAX_VALUE}) {
            assertEquals(id, json.readValue("{\"tenantId\":\"" + id + "\"}",
                    SwitchTenantRequest.class).tenantId());
            assertEquals(id, json.readValue("{\"tenantId\":" + id + "}",
                    SwitchTenantRequest.class).tenantId());
        }
    }

    @Test
    void malformedOrAmbiguousIdTokensMustBeRejectedRatherThanCoerced() {
        for (String token : List.of("1.0", "1e3", "true", "null", "0", "-1",
                "\" 1\"", "\"1 \"", "\"+1\"", "\"01\"", "\"1e3\"",
                "9223372036854775808", "\"9223372036854775808\"")) {
            assertThrows(JsonProcessingException.class, () -> json.readValue(
                    "{\"tenantId\":" + token + "}", SwitchTenantRequest.class), token);
        }
        assertThrows(JsonProcessingException.class, () -> json.readValue(
                "{\"datasetVersionId\":1,\"metricVersionIds\":[1.0],\"limit\":5}", QueryPlan.class));
    }
}
