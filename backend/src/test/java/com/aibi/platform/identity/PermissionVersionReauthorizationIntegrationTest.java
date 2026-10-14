package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
class PermissionVersionReauthorizationIntegrationTest extends IdentityApiTestSupport {

    @Autowired
    private TaskAuthorizationService taskAuthorizationService;

    @Test
    void shouldRejectOldTokenAndRunningTaskAfterPermissionVersionChanges() throws Exception {
        TenantFixture actor = tenantFixture("permission-version");
        JsonNode workspace = request(
                post("/api/v1/tenants/{tenantId}/workspaces", actor.tenantId()), """
                        {"workspaceCode":"reauth_space","workspaceName":"重鉴权空间"}
                        """, actor.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();
        JsonNode role = request(post("/api/v1/tenants/{tenantId}/roles", actor.tenantId()), """
                {"roleCode":"reauth_reader","roleName":"运行任务读者"}
                """, actor.tenantToken(), status().isCreated());
        long roleId = role.path("id").asLong();
        request(post("/api/v1/tenants/{tenantId}/roles/{roleId}/permissions",
                actor.tenantId(), roleId), """
                {"permissionId":7,"effect":"ALLOW"}
                """, actor.tenantToken(), status().isOk());

        String actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        Target reader = createTarget(actor.tenantId(), workspaceId, actorContext);
        request(post("/api/v1/tenants/{tenantId}/members/{memberId}/roles",
                actor.tenantId(), reader.memberId()), """
                {"roleId":%d,"workspaceId":%d,"memberLockVersion":1}
                """.formatted(roleId, workspaceId), actorContext, status().isCreated());

        String readerContext = switchTenant(reader.accessToken(), actor.tenantId());
        AccessCheckRequest access = new AccessCheckRequest(
                workspaceId, "dataset:read", null, "read");
        TaskAuthorizationSnapshot snapshot = taskAuthorizationService.capture(
                "Bearer " + readerContext, actor.tenantId(), access);
        assertDoesNotThrow(() -> taskAuthorizationService.reauthorize(snapshot));

        request(post("/api/v1/tenants/{tenantId}/roles/{roleId}/permissions",
                actor.tenantId(), roleId), """
                {"permissionId":7,"effect":"DENY"}
                """, actorContext, status().isOk());

        request(post("/api/v1/tenants/{tenantId}/access/require", actor.tenantId()), """
                {"workspaceId":%d,"permissionCode":"dataset:read","actionCode":"read"}
                """.formatted(workspaceId), readerContext, status().isForbidden());
        assertThrows(ForbiddenException.class,
                () -> taskAuthorizationService.reauthorize(snapshot));
    }

    private Target createTarget(long tenantId, long workspaceId, String actorContext)
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "reauth-target-" + suffix + "@example.com";
        JsonNode user = request(post("/api/v1/auth/register"), """
                {"email":"%s","displayName":"重鉴权成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/v1/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        JsonNode member = request(post("/api/v1/tenants/{tenantId}/members", tenantId), """
                {"userId":%d,"memberName":"重鉴权成员"}
                """.formatted(user.path("id").asLong()), actorContext, status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/workspaces/{workspaceId}/members",
                tenantId, workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(member.path("id").asLong()), actorContext, status().isNoContent());
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
