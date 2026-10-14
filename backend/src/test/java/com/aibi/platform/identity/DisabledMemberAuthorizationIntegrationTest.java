package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisabledMemberAuthorizationIntegrationTest extends IdentityApiTestSupport {

    @Autowired
    private TaskAuthorizationService taskAuthorizationService;

    @Test
    void shouldRejectOldTokenAndAsyncTaskAfterMemberIsDisabled() throws Exception {
        TenantFixture administrator = tenantFixture("disabled-member");
        JsonNode workspace = request(post(
                "/api/v1/tenants/{tenantId}/workspaces", administrator.tenantId()), """
                        {"workspaceCode":"disabled_space","workspaceName":"停用验证空间"}
                        """, administrator.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();
        Target target = createTarget(administrator, workspaceId);
        JsonNode asset = request(post(
                "/api/v1/tenants/{tenantId}/assets", administrator.tenantId()), """
                        {"workspaceId":%d,"assetType":"DATASET","assetRefId":500050}
                        """.formatted(workspaceId), administrator.tenantToken(), status().isCreated());
        long assetId = asset.path("id").asLong();
        JsonNode role = request(post("/api/v1/tenants/{t}/roles", administrator.tenantId()),
                "{\"roleCode\":\"disabled_reader\",\"roleName\":\"停用测试读者\"}",
                administrator.tenantToken(), status().isCreated());
        request(post("/api/v1/tenants/{t}/roles/{r}/permissions", administrator.tenantId(), role.path("id").asLong()),
                "{\"permissionId\":7,\"effect\":\"ALLOW\"}", administrator.tenantToken(), status().isOk());
        String administratorContext = switchTenant(administrator.accessToken(), administrator.tenantId());
        request(post("/api/v1/tenants/{t}/members/{m}/roles", administrator.tenantId(), target.memberId()),
                "{\"roleId\":" + role.path("id").asLong() + ",\"workspaceId\":" + workspaceId
                        + ",\"memberLockVersion\":1}", administratorContext, status().isCreated());
        String targetContext = switchTenant(target.accessToken(), administrator.tenantId());
        AccessCheckRequest access = new AccessCheckRequest(
                workspaceId, "dataset:read", assetId, "read");
        TaskAuthorizationSnapshot asyncTask = taskAuthorizationService.capture(
                "Bearer " + targetContext, administrator.tenantId(), access);

        request(post("/api/v1/tenants/{tenantId}/members/{memberId}/disable",
                administrator.tenantId(), target.memberId()), """
                {"lockVersion":2}
                """, administratorContext, status().isOk());

        request(post("/api/v1/tenants/{tenantId}/access/require", administrator.tenantId()), """
                {"workspaceId":%d,"permissionCode":"dataset:read",
                 "assetId":%d,"actionCode":"read"}
                """.formatted(workspaceId, assetId), targetContext, status().isForbidden());
        assertThrows(ForbiddenException.class,
                () -> taskAuthorizationService.reauthorize(asyncTask));
        request(post("/api/v1/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(administrator.tenantId()),
                target.accessToken(), status().isForbidden());
    }

    private Target createTarget(TenantFixture administrator, long workspaceId) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "disabled-target-" + suffix + "@example.com";
        JsonNode user = request(post("/api/v1/auth/register"), """
                {"email":"%s","displayName":"待停用成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/v1/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        JsonNode member = request(post(
                "/api/v1/tenants/{tenantId}/members", administrator.tenantId()), """
                        {"userId":%d,"memberName":"待停用成员"}
                        """.formatted(user.path("id").asLong()), administrator.tenantToken(), status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/workspaces/{workspaceId}/members",
                administrator.tenantId(), workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(member.path("id").asLong()),
                administrator.tenantToken(), status().isNoContent());
        return new Target(member.path("id").asLong(), login.path("accessToken").asText());
    }

    private String switchTenant(String accessToken, long tenantId) throws Exception {
        return request(post("/api/v1/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk())
                .path("tenantAccessToken").asText();
    }

    private record Target(long memberId, String accessToken) {
    }
}
