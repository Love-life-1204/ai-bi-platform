package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivilegeBoundaryRepairTest extends IdentityApiTestSupport {

    @Test
    void ordinaryMemberCannotGrantRolesOrCreateWorkspaces() throws Exception {
        TenantFixture owner = tenantFixture("bound");
        Regular member = regular(owner);
        request(post("/api/tenants/{t}/roles", owner.tenantId()),
                "{\"roleCode\":\"self_admin\",\"roleName\":\"自行提权\"}", member.tenant(), status().isForbidden());
        request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"unauthorized\",\"workspaceName\":\"越权空间\"}", member.tenant(), status().isForbidden());
    }

    @Test
    void ordinaryMemberCannotAddMembersOrMutateTenant() throws Exception {
        TenantFixture owner = tenantFixture("bound_member");
        Regular member = regular(owner);
        request(post("/api/tenants/{t}/members", owner.tenantId()),
                "{\"userId\":" + owner.userId() + ",\"memberName\":\"越权添加\"}", member.tenant(), status().isForbidden());
        request(put("/api/tenants/{t}", owner.tenantId()),
                "{\"tenantName\":\"越权修改\",\"timezone\":\"UTC\",\"defaultCurrency\":\"USD\",\"lockVersion\":0}",
                member.tenant(), status().isForbidden());
        request(post("/api/tenants/{t}/invitations", owner.tenantId()),
                "{\"email\":\"attack@example.com\",\"expiresInHours\":24}", member.tenant(), status().isForbidden());
    }

    @Test
    void globalLoginCannotWritePlatformPermissionDictionary() throws Exception {
        TenantFixture owner = tenantFixture("dict");
        request(post("/api/permissions"),
                "{\"permissionCode\":\"attack:all\",\"permissionName\":\"伪造权限\",\"resourceType\":\"TENANT\",\"actionCode\":\"all\"}",
                owner.accessToken(), status().isUnauthorized());
    }

    @Test
    void ownerRecoversFrozenTenantButOldTokenNeverRevives() throws Exception {
        TenantFixture owner = tenantFixture("freeze");
        Regular member = regular(owner);
        JsonNode frozen = request(post("/api/tenants/{t}/freeze", owner.tenantId()),
                "{\"lockVersion\":0}", owner.tenantToken(), status().isOk());
        assertEquals(2, frozen.path("permissionVersion").asLong());
        request(get("/api/tenants/{t}", owner.tenantId()), null, owner.tenantToken(), status().isForbidden());
        request(post("/api/tenants/{t}/unfreeze", owner.tenantId()),
                "{\"lockVersion\":1}", member.global(), status().isForbidden());
        JsonNode active = request(post("/api/tenants/{t}/unfreeze", owner.tenantId()),
                "{\"lockVersion\":1}", owner.accessToken(), status().isOk());
        assertEquals(3, active.path("permissionVersion").asLong());
        request(get("/api/tenants/{t}", owner.tenantId()), null, owner.tenantToken(), status().isForbidden());
        String fresh = request(post("/api/auth/switch-tenant"), "{\"tenantId\":" + owner.tenantId() + "}",
                owner.accessToken(), status().isOk()).path("tenantAccessToken").asText();
        request(get("/api/tenants/{t}", owner.tenantId()), null, fresh, status().isOk());
    }

    @Test
    void ownerCannotBeDisabledOrRemovedByGeneralMemberApi() throws Exception {
        TenantFixture owner = tenantFixture("last_owner");
        request(post("/api/tenants/{t}/members/{m}/disable", owner.tenantId(), owner.memberId()),
                "{\"lockVersion\":0}", owner.tenantToken(), status().isForbidden());
        request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/tenants/{t}/members/{m}", owner.tenantId(), owner.memberId()).param("lockVersion", "0"),
                null, owner.tenantToken(), status().isForbidden());
    }

    private Regular regular(TenantFixture owner) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        JsonNode user = request(post("/api/auth/register"), "{\"email\":\"" + email
                + "\",\"displayName\":\"普通成员\",\"password\":\"correct-horse-battery-staple\"}", null, status().isCreated());
        String global = request(post("/api/auth/login"), "{\"email\":\"" + email
                + "\",\"password\":\"correct-horse-battery-staple\"}", null, status().isOk()).path("accessToken").asText();
        request(post("/api/tenants/{t}/members", owner.tenantId()),
                "{\"userId\":" + user.path("id").asLong() + ",\"memberName\":\"普通成员\"}",
                owner.tenantToken(), status().isCreated());
        String tenant = request(post("/api/auth/switch-tenant"), "{\"tenantId\":" + owner.tenantId() + "}",
                global, status().isOk()).path("tenantAccessToken").asText();
        return new Regular(global, tenant);
    }

    private record Regular(String global, String tenant) {
    }
}
