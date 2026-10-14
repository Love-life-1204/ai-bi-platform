package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceIdHttpRepairTest extends IdentityApiTestSupport {
    @Test
    void nonCanonicalPathAndHeaderIdsMustBeRejected() throws Exception {
        TenantFixture owner = tenantFixture("http_id");
        request(get("/api/tenants/{id}", "0" + owner.tenantId()), null,
                owner.tenantToken(), status().isBadRequest());
        request(get("/api/datasources").header("X-Tenant-Id", "0" + owner.tenantId()),
                null, owner.tenantToken(), status().isBadRequest());
    }

    @Test
    void canonicalLargeTenantIdMustBeReturnedUnchangedAsString() throws Exception {
        TenantFixture owner = tenantFixture("http_id_exact");
        var tenant = request(get("/api/tenants/{id}", owner.tenantId()), null,
                owner.tenantToken(), status().isOk());
        assertTrue(tenant.path("id").isTextual());
        assertEquals(Long.toString(owner.tenantId()), tenant.path("id").textValue());
    }

    @Test
    void malformedQueryIdsMustReturn400() throws Exception {
        TenantFixture owner = tenantFixture("http_id_query");
        for (String id : new String[]{"01", "+1", " 1", "1.0", "1e3", "9223372036854775808"}) {
            request(get("/api/datasources").header("X-Tenant-Id", owner.tenantId())
                    .param("workspaceId", id), null, owner.tenantToken(), status().isBadRequest());
        }
    }
}
