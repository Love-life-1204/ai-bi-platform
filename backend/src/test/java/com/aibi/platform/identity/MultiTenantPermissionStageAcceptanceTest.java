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
class MultiTenantPermissionStageAcceptanceTest extends IdentityApiTestSupport {

    @Test
    void shouldSwitchSameUserAndEnforceDifferentTenantRoles() throws Exception {
        TenantFixture tenantA = tenantFixture("stage-a");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        JsonNode tenantB = request(post("/api/v1/tenants"), """
                {"tenantCode":"stage_b_%s","tenantName":"阶段验收 B"}
                """.formatted(suffix), tenantA.accessToken(), status().isCreated());
        long tenantBId = tenantB.path("id").asLong();
        JsonNode memberB = request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/users/me/memberships"), null, tenantA.accessToken(), status().isOk());
        for (JsonNode member : memberB) {
            if (member.path("tenantId").asLong() == tenantBId) { memberB = member; break; }
        }

        String contextA = switchTenant(tenantA.accessToken(), tenantA.tenantId());
        String contextB = switchTenant(tenantA.accessToken(), tenantBId);
        long workspaceA = createWorkspace(tenantA.tenantId(), contextA);
        long workspaceB = createWorkspace(tenantBId, contextB);
        long roleA = createRole(tenantA.tenantId(), contextA, "reader_allow");
        long roleB = createRole(tenantBId, contextB, "reader_deny");
        setDatasetRead(tenantA.tenantId(), contextA, roleA, "ALLOW");
        setDatasetRead(tenantBId, contextB, roleB, "DENY");

        contextA = switchTenant(tenantA.accessToken(), tenantA.tenantId());
        contextB = switchTenant(tenantA.accessToken(), tenantBId);
        assignRole(tenantA.tenantId(), contextA, tenantA.memberId(), roleA, workspaceA);
        assignRole(tenantBId, contextB, memberB.path("id").asLong(), roleB, workspaceB);
        contextA = switchTenant(tenantA.accessToken(), tenantA.tenantId());
        contextB = switchTenant(tenantA.accessToken(), tenantBId);

        JsonNode policy = request(post(
                "/api/v1/tenants/{tenantId}/data-policies", tenantA.tenantId()), """
                        {"datasetId":9900,"policyCode":"only_east","policyName":"仅华东",
                         "policyAst":{"op":"eq","field":"region","value":"east"},
                         "policyGroup":"region"}
                        """, contextA, status().isCreated());
        request(post("/api/v1/tenants/{tenantId}/data-policies/{policyId}/grants",
                tenantA.tenantId(), policy.path("id").asLong()), """
                {"principalType":"ROLE","principalId":%d}
                """.formatted(roleA), contextA, status().isCreated());
        contextA = switchTenant(tenantA.accessToken(), tenantA.tenantId());

        String bodyA = evaluationBody(workspaceA);
        mockMvc.perform(post("/api/v1/tenants/{tenantId}/data-policies/evaluate",
                        tenantA.tenantId())
                        .header("Authorization", "Bearer " + contextA)
                        .contentType(MediaType.APPLICATION_JSON).content(bodyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowPolicyGroups.region[0].value").value("east"));
        request(post("/api/v1/tenants/{tenantId}/data-policies/evaluate", tenantBId),
                evaluationBody(workspaceB), contextB, status().isForbidden());
        request(post("/api/v1/tenants/{tenantId}/data-policies/evaluate", tenantBId),
                evaluationBody(workspaceB), contextA, status().isForbidden());
    }

    private long createWorkspace(long tenantId, String context) throws Exception {
        return request(post("/api/v1/tenants/{tenantId}/workspaces", tenantId), """
                {"workspaceCode":"shared_stage","workspaceName":"阶段空间"}
                """, context, status().isCreated()).path("id").asLong();
    }

    private long createRole(long tenantId, String context, String roleCode) throws Exception {
        return request(post("/api/v1/tenants/{tenantId}/roles", tenantId), """
                {"roleCode":"%s","roleName":"阶段角色"}
                """.formatted(roleCode), context, status().isCreated()).path("id").asLong();
    }

    private void setDatasetRead(long tenantId, String context, long roleId, String effect)
            throws Exception {
        request(post("/api/v1/tenants/{tenantId}/roles/{roleId}/permissions",
                tenantId, roleId), """
                {"permissionId":7,"effect":"%s"}
                """.formatted(effect), context, status().isOk());
    }

    private void assignRole(long tenantId, String context, long memberId,
            long roleId, long workspaceId) throws Exception {
        request(post("/api/v1/tenants/{tenantId}/members/{memberId}/roles",
                tenantId, memberId), """
                {"roleId":%d,"workspaceId":%d,"memberLockVersion":0}
                """.formatted(roleId, workspaceId), context, status().isCreated());
    }

    private String evaluationBody(long workspaceId) {
        return """
                {"workspaceId":%d,"datasetId":9900,"datasetFieldIds":[9901]}
                """.formatted(workspaceId);
    }

    private String switchTenant(String accessToken, long tenantId) throws Exception {
        return request(post("/api/v1/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk())
                .path("tenantAccessToken").asText();
    }
}
