package com.aibi.platform.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aibi.platform.common.exception.TooManyRequestsException;
import com.aibi.platform.query.config.QueryProperties;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.manager.QueryResourceManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QueryContractSecurityTest {

    @Test
    void rejectsRawSqlAndUnknownAuthorizationFieldsAtJsonBoundary() {
        ObjectMapper mapper = new ObjectMapper();

        assertThatThrownBy(() -> mapper.readValue("""
                {"datasetVersionId":1,"metricVersionIds":[2],"limit":10,
                 "sql":"DROP TABLE system.users","tenantId":999}
                """, QueryPlan.class)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void rejectsWhenTenantQueueIsFull() {
        QueryProperties properties = new QueryProperties();
        properties.setGlobalConcurrency(1);
        properties.setTenantConcurrency(1);
        properties.setQueueCapacity(0);
        QueryResourceManager manager = new QueryResourceManager(properties);

        try (QueryResourceManager.Lease ignored = manager.acquire(1)) {
            assertThatThrownBy(() -> manager.acquire(1))
                    .isInstanceOf(TooManyRequestsException.class);
        }
    }

    @Test
    void noisyTenantCannotConsumeAnotherTenantsReservedSlot() {
        QueryProperties properties = new QueryProperties();
        properties.setGlobalConcurrency(2);
        properties.setTenantConcurrency(1);
        properties.setQueueCapacity(0);
        QueryResourceManager manager = new QueryResourceManager(properties);

        try (QueryResourceManager.Lease ignored = manager.acquire(1)) {
            assertThatThrownBy(() -> manager.acquire(1))
                    .isInstanceOf(TooManyRequestsException.class);
            assertThatCode(() -> {
                try (QueryResourceManager.Lease other = manager.acquire(2)) {
                    // Tenant 2 keeps its fair share while tenant 1 is saturated.
                }
            }).doesNotThrowAnyException();
        }
    }
}
