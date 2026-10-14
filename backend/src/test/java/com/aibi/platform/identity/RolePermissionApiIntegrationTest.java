package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "app.commercial.platform-operations-token=test-platform-dictionary-secret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolePermissionApiIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldManagePermissionDictionaryRolesAndExplicitEffects() throws Exception {
        JsonNode permission = request(post("/api/permissions").header("X-Platform-Token", "test-platform-dictionary-secret"), """
                {"permissionCode":"finance:approve","permissionName":"财务审批",
                 "resourceType":"FINANCE","actionCode":"approve"}
                """, null, status().isCreated());
        TenantFixture fixture = tenantFixture("role");

        request(get("/api/permissions"), null, fixture.accessToken(), status().isOk());
        JsonNode role = request(post("/api/tenants/{tenantId}/roles", fixture.tenantId()), """
                {"roleCode":"finance_admin","roleName":"财务管理员"}
                """, fixture.tenantToken(), status().isCreated());
        long roleId = role.path("id").asLong();

        request(post("/api/tenants/{tenantId}/roles/{roleId}/permissions",
                fixture.tenantId(), roleId), """
                {"permissionId":%d,"effect":"ALLOW"}
                """.formatted(permission.path("id").asLong()), fixture.tenantToken(), status().isOk());
        String tenantToken = switchTenant(fixture);
        request(post("/api/tenants/{tenantId}/roles/{roleId}/permissions",
                fixture.tenantId(), roleId), """
                {"permissionId":%d,"effect":"DENY"}
                """.formatted(permission.path("id").asLong()), tenantToken, status().isOk());
        tenantToken = switchTenant(fixture);

        mockMvc.perform(get("/api/tenants/{tenantId}/roles/{roleId}/permissions",
                        fixture.tenantId(), roleId)
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].permissionCode").value("finance:approve"))
                .andExpect(jsonPath("$.data[0].effect").value("DENY"));

        request(put("/api/tenants/{tenantId}/roles/{roleId}", fixture.tenantId(), roleId), """
                {"roleName":"财务只读","roleStatus":"DISABLED","lockVersion":0}
                """, tenantToken, status().isOk());
        tenantToken = switchTenant(fixture);
        request(post("/api/tenants/{tenantId}/roles", fixture.tenantId()), """
                {"roleCode":"finance_admin","roleName":"重复角色"}
                """, tenantToken, status().isConflict());
    }

    private String switchTenant(TenantFixture fixture) throws Exception {
        return request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(fixture.tenantId()), fixture.accessToken(), status().isOk())
                .path("tenantAccessToken").asText();
    }
}
