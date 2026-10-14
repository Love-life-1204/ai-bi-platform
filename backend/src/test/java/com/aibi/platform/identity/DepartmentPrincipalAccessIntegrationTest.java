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
class DepartmentPrincipalAccessIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldApplyDepartmentAclRlsAndCls() throws Exception {
        TenantFixture actor = tenantFixture("department-principal");
        JsonNode workspace = request(post(
                "/api/v1/tenants/{tenantId}/workspaces", actor.tenantId()), """
                        {"workspaceCode":"department_scope","workspaceName":"部门权限空间"}
                        """, actor.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();
        JsonNode asset = request(post("/api/v1/tenants/{tenantId}/assets", actor.tenantId()), """
                {"workspaceId":%d,"assetType":"DATASET","assetRefId":7300}
                """.formatted(workspaceId), actor.tenantToken(), status().isCreated());
        Target target = createTarget(actor, workspaceId);

        JsonNode role = request(post("/api/v1/tenants/{tenantId}/roles", actor.tenantId()), """
                {"roleCode":"department_reader","roleName":"部门读者"}
                """, actor.tenantToken(), status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/roles/{roleId}/permissions",
                actor.tenantId(), role.path("id").asLong()), """
                {"permissionId":7,"effect":"ALLOW"}
                """, actor.tenantToken(), status().isOk());
        String actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        request(post("/api/v1/tenants/{tenantId}/members/{memberId}/roles",
                actor.tenantId(), target.memberId()), """
                {"roleId":%d,"workspaceId":%d,"memberLockVersion":1}
                """.formatted(role.path("id").asLong(), workspaceId),
                actorContext, status().isCreated());

        JsonNode department = request(post(
                "/api/v1/tenants/{tenantId}/departments", actor.tenantId()), """
                        {"departmentCode":"east_sales","departmentName":"华东销售", "sortOrder":1}
                        """, actorContext, status().isCreated());
        long departmentId = department.path("id").asLong();
        request(post("/api/v1/tenants/{tenantId}/departments/{departmentId}/members",
                actor.tenantId(), departmentId), """
                {"memberId":%d,"primary":true,"memberLockVersion":2}
                """.formatted(target.memberId()), actorContext, status().isNoContent());

        JsonNode rowPolicy = request(post(
                "/api/v1/tenants/{tenantId}/data-policies", actor.tenantId()), """
                        {"datasetId":7300,"policyCode":"department_east",
                         "policyName":"华东部门范围",
                         "policyAst":{"op":"eq","field":"department","value":"east"},
                         "policyGroup":"department"}
                        """, actorContext, status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/data-policies/{policyId}/grants",
                actor.tenantId(), rowPolicy.path("id").asLong()), """
                {"principalType":"DEPARTMENT","principalId":%d}
                """.formatted(departmentId), actorContext, status().isCreated());
        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        request(post("/api/v1/tenants/{tenantId}/column-policies", actor.tenantId()), """
                {"datasetFieldId":7301,"principalType":"DEPARTMENT","principalId":%d,
                 "accessMode":"MASKED","maskConfig":{"algorithm":"LAST4"}}
                """.formatted(departmentId), actorContext, status().isOk());
        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        request(post("/api/v1/tenants/{tenantId}/assets/{assetId}/acl",
                actor.tenantId(), asset.path("id").asLong()), """
                {"principalType":"DEPARTMENT","principalId":%d,
                 "actionCode":"read","effect":"DENY"}
                """.formatted(departmentId), actorContext, status().isNoContent());

        String targetContext = switchTenant(target.accessToken(), actor.tenantId());
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/data-policies/evaluate",
                        actor.tenantId())
                        .header("Authorization", "Bearer " + targetContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":%d,"datasetId":7300,
                                 "datasetFieldIds":[7301]}
                                """.formatted(workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowPolicyGroups.department[0].value").value("east"))
                .andExpect(jsonPath("$.data.columns[0].accessMode").value("MASKED"));
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/access/check", actor.tenantId())
                        .header("Authorization", "Bearer " + targetContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":%d,"permissionCode":"dataset:read",
                                 "assetId":%d,"actionCode":"read"}
                                """.formatted(workspaceId, asset.path("id").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reason").value("ACL_EXPLICIT_DENY"));
    }

    private Target createTarget(TenantFixture actor, long workspaceId) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "department-target-" + suffix + "@example.com";
        JsonNode user = request(post("/api/v1/auth/register"), """
                {"email":"%s","displayName":"部门成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/v1/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        JsonNode member = request(post("/api/v1/tenants/{tenantId}/members", actor.tenantId()), """
                {"userId":%d,"memberName":"部门成员"}
                """.formatted(user.path("id").asLong()), actor.tenantToken(), status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/workspaces/{workspaceId}/members",
                actor.tenantId(), workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(member.path("id").asLong()),
                actor.tenantToken(), status().isNoContent());
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
