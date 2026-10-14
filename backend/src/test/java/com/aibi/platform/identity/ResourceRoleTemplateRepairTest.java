package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.identity.service.RoleTemplateCatalog;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceRoleTemplateRepairTest extends IdentityApiTestSupport {

    @Test
    void templatesPersistExactActionMatrixWithoutIdentityPrivileges() throws Exception {
        var owner = tenantFixture("templates");
        for (String code : new String[]{"admin", "analyst", "viewer"}) {
            var role = request(post("/api/tenants/{t}/roles", owner.tenantId()),
                    "{\"roleCode\":\""+code+"\",\"roleName\":\"资源角色\"}", owner.tenantToken(), status().isCreated());
            var rows = request(get("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role.path("id").asLong()),
                    null, owner.tenantToken(), status().isOk());
            var actual = new HashSet<String>();
            rows.forEach(row -> { assertEquals("ALLOW", row.path("effect").asText()); actual.add(row.path("permissionCode").asText()); });
            assertEquals(RoleTemplateCatalog.permissions(code), actual);
            assertFalse(actual.contains("role:manage"));
            assertFalse(actual.contains("member:manage"));
            assertFalse(actual.contains("tenant:manage"));
            request(post("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role.path("id").asLong()),
                    "{\"permissionId\":1,\"effect\":\"ALLOW\"}", owner.tenantToken(), status().isForbidden());
        }
    }

    @Test
    void customRoleStartsWithNoPermissions() throws Exception {
        var owner = tenantFixture("custom_role");
        var role = request(post("/api/tenants/{t}/roles", owner.tenantId()),
                "{\"roleCode\":\"custom_empty\",\"roleName\":\"默认拒绝\"}", owner.tenantToken(), status().isCreated());
        assertEquals(0, request(get("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role.path("id").asLong()),
                null, owner.tenantToken(), status().isOk()).size());
    }
}
