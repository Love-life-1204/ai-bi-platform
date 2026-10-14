package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacAclIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldEnforceRbacWorkspaceMembershipAndAssetAcl() throws Exception {
        TenantFixture actor = tenantFixture("rbac");
        JsonNode workspace = request(
                post("/api/tenants/{tenantId}/workspaces", actor.tenantId()), """
                        {"workspaceCode":"analytics","workspaceName":"分析空间"}
                        """, actor.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();
        JsonNode asset = request(post("/api/tenants/{tenantId}/assets", actor.tenantId()), """
                {"workspaceId":%d,"assetType":"DATASET","assetRefId":9001}
                """.formatted(workspaceId), actor.tenantToken(), status().isCreated());
        long assetId = asset.path("id").asLong();

        JsonNode role = request(post("/api/tenants/{tenantId}/roles", actor.tenantId()), """
                {"roleCode":"dataset_reader","roleName":"数据集读者"}
                """, actor.tenantToken(), status().isCreated());
        request(post("/api/tenants/{tenantId}/roles/{roleId}/permissions",
                actor.tenantId(), role.path("id").asLong()), """
                {"permissionId":7,"effect":"ALLOW"}
                """, actor.tenantToken(), status().isOk());
        String actorContext = switchTenant(actor.accessToken(), actor.tenantId());

        Target target = target(actor, workspaceId, actorContext);
        String contextBeforeRole = switchTenant(target.accessToken(), actor.tenantId());
        requireDatasetRead(actor.tenantId(), workspaceId, assetId, contextBeforeRole, false);

        request(post("/api/tenants/{tenantId}/members/{memberId}/roles",
                actor.tenantId(), target.memberId()), """
                {"roleId":%d,"workspaceId":%d,"memberLockVersion":1}
                """.formatted(role.path("id").asLong(), workspaceId),
                actorContext, status().isCreated());

        requireDatasetRead(actor.tenantId(), workspaceId, assetId, contextBeforeRole, false);
        String contextWithRole = switchTenant(target.accessToken(), actor.tenantId());
        requireDatasetRead(actor.tenantId(), workspaceId, assetId, contextWithRole, true);

        request(post("/api/tenants/{tenantId}/assets/{assetId}/acl",
                actor.tenantId(), assetId), """
                {"principalType":"MEMBER","principalId":%d,"actionCode":"read","effect":"DENY"}
                """.formatted(target.memberId()), actorContext, status().isNoContent());
        requireDatasetRead(actor.tenantId(), workspaceId, assetId, contextWithRole, false);
        String contextAfterAcl = switchTenant(target.accessToken(), actor.tenantId());

        mockMvc.perform(post("/api/tenants/{tenantId}/access/check", actor.tenantId())
                        .header("Authorization", "Bearer " + contextAfterAcl)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(checkBody(workspaceId, assetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reason").value("ACL_EXPLICIT_DENY"));
    }

    private Target target(TenantFixture actor, long workspaceId, String actorContext)
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "rbac-target-" + suffix + "@example.com";
        JsonNode user = request(post("/api/auth/register"), """
                {"email":"%s","displayName":"RBAC目标","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        JsonNode member = request(post("/api/tenants/{tenantId}/members", actor.tenantId()), """
                {"userId":%d,"memberName":"RBAC目标"}
                """.formatted(user.path("id").asLong()), actorContext, status().isCreated());
        request(post("/api/tenants/{tenantId}/workspaces/{workspaceId}/members",
                actor.tenantId(), workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(member.path("id").asLong()), actorContext, status().isNoContent());
        return new Target(member.path("id").asLong(), login.path("accessToken").asText());
    }

    private String switchTenant(String accessToken, long tenantId) throws Exception {
        return request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk())
                .path("tenantAccessToken").asText();
    }

    private void requireDatasetRead(long tenantId, long workspaceId, long assetId,
            String tenantToken, boolean allowed) throws Exception {
        var expected = allowed ? status().isOk() : status().isForbidden();
        request(post("/api/tenants/{tenantId}/access/require", tenantId),
                checkBody(workspaceId, assetId), tenantToken, expected);
    }

    private String checkBody(long workspaceId, long assetId) {
        return """
                {"workspaceId":%d,"permissionCode":"dataset:read", "assetId":%d,"actionCode":"read"}
                """.formatted(workspaceId, assetId);
    }

    private record Target(long memberId, String accessToken) {
    }
}
