package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDataIsolationIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldIsolateSameBusinessIdBetweenTwoTenants() throws Exception {
        TenantFixture first = tenantFixture("isolation-a");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        JsonNode secondTenant = request(post("/api/v1/tenants"), """
                {"tenantCode":"isolation_b_%s","tenantName":"隔离企业 B"}
                """.formatted(suffix), first.accessToken(), status().isCreated());
        long secondTenantId = secondTenant.path("id").asLong();
        String firstContext = switchTenant(first.accessToken(), first.tenantId());
        String secondContext = switchTenant(first.accessToken(), secondTenantId);
        long firstWorkspace = createWorkspace(first.tenantId(), firstContext);
        long secondWorkspace = createWorkspace(secondTenantId, secondContext);
        long firstAsset = createAsset(first.tenantId(), firstContext, firstWorkspace);
        long secondAsset = createAsset(secondTenantId, secondContext, secondWorkspace);

        requireOwnAsset(first.tenantId(), firstContext, firstWorkspace, firstAsset);
        requireOwnAsset(secondTenantId, secondContext, secondWorkspace, secondAsset);

        request(post("/api/v1/tenants/{tenantId}/access/require", first.tenantId()),
                accessBody(firstWorkspace, secondAsset), firstContext, status().isNotFound());
        request(post("/api/v1/tenants/{tenantId}/access/require", secondTenantId),
                accessBody(secondWorkspace, firstAsset), secondContext, status().isNotFound());
        request(post("/api/v1/tenants/{tenantId}/access/require", secondTenantId),
                accessBody(secondWorkspace, secondAsset), firstContext, status().isForbidden());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/access/check", first.tenantId())
                        .header("Authorization", "Bearer " + firstContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessBody(firstWorkspace, firstAsset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.reason").value("ASSET_OWNER"));
    }

    private long createWorkspace(long tenantId, String tenantContext) throws Exception {
        return request(post("/api/v1/tenants/{tenantId}/workspaces", tenantId), """
                {"workspaceCode":"shared_business_code","workspaceName":"同业务空间"}
                """, tenantContext, status().isCreated()).path("id").asLong();
    }

    private long createAsset(long tenantId, String tenantContext, long workspaceId)
            throws Exception {
        return request(post("/api/v1/tenants/{tenantId}/assets", tenantId), """
                {"workspaceId":%d,"assetType":"DATASET","assetRefId":880088}
                """.formatted(workspaceId), tenantContext, status().isCreated())
                .path("id").asLong();
    }

    private void requireOwnAsset(long tenantId, String tenantContext,
            long workspaceId, long assetId) throws Exception {
        request(post("/api/v1/tenants/{tenantId}/access/require", tenantId),
                accessBody(workspaceId, assetId), tenantContext, status().isOk());
    }

    private String accessBody(long workspaceId, long assetId) {
        return """
                {"workspaceId":%d,"permissionCode":"dataset:read",
                 "assetId":%d,"actionCode":"read"}
                """.formatted(workspaceId, assetId);
    }

    private String switchTenant(String accessToken, long tenantId) throws Exception {
        return request(post("/api/v1/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk())
                .path("tenantAccessToken").asText();
    }
}
