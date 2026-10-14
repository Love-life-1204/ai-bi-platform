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
class RlsClsIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldApplyRlsAndPreferExplicitColumnDeny() throws Exception {
        TenantFixture actor = tenantFixture("data-policy");
        JsonNode workspace = request(
                post("/api/tenants/{tenantId}/workspaces", actor.tenantId()), """
                        {"workspaceCode":"policy_space","workspaceName":"策略空间"}
                        """, actor.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();
        Target reader = createTarget(actor, workspaceId, "reader");

        JsonNode role = request(post("/api/tenants/{tenantId}/roles", actor.tenantId()), """
                {"roleCode":"rls_reader","roleName":"行级读者"}
                """, actor.tenantToken(), status().isCreated());
        long roleId = role.path("id").asLong();
        request(post("/api/tenants/{tenantId}/roles/{roleId}/permissions",
                actor.tenantId(), roleId), """
                {"permissionId":7,"effect":"ALLOW"}
                """, actor.tenantToken(), status().isOk());
        String actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        request(post("/api/tenants/{tenantId}/members/{memberId}/roles",
                actor.tenantId(), reader.memberId()), """
                {"roleId":%d,"workspaceId":%d,"memberLockVersion":1}
                """.formatted(roleId, workspaceId), actorContext, status().isCreated());

        JsonNode regionPolicy = request(
                post("/api/tenants/{tenantId}/data-policies", actor.tenantId()), """
                        {"datasetId":7001,"policyCode":"region_east","policyName":"华东区域",
                         "policyAst":{"op":"eq","field":"region","value":"east"},
                         "policyGroup":"region"}
                        """, actorContext, status().isCreated());
        request(post("/api/tenants/{tenantId}/data-policies/{policyId}/grants",
                actor.tenantId(), regionPolicy.path("id").asLong()), """
                {"principalType":"ROLE","principalId":%d}
                """.formatted(roleId), actorContext, status().isCreated());

        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        setColumnPolicy(actor, actorContext, 7101, "MEMBER", reader.memberId(), "MASKED",
                "{\"algorithm\":\"LAST4\"}");
        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        setColumnPolicy(actor, actorContext, 7101, "ROLE", roleId, "DENY", null);
        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        setColumnPolicy(actor, actorContext, 7102, "MEMBER", reader.memberId(), "MASKED",
                "{\"algorithm\":\"EMAIL\"}");

        String readerContext = switchTenant(reader.accessToken(), actor.tenantId());
        String evaluationBody = """
                {"workspaceId":%d,"datasetId":7001,"datasetFieldIds":[7101,7102,7103]}
                """.formatted(workspaceId);
        mockMvc.perform(post("/api/tenants/{tenantId}/data-policies/evaluate",
                        actor.tenantId())
                        .header("Authorization", "Bearer " + readerContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowPolicyGroups.region[0].field").value("region"))
                .andExpect(jsonPath("$.data.columns[0].accessMode").value("DENY"))
                .andExpect(jsonPath("$.data.columns[1].accessMode").value("MASKED"))
                .andExpect(jsonPath("$.data.columns[1].maskConfig.algorithm").value("EMAIL"))
                .andExpect(jsonPath("$.data.columns[2].accessMode").value("VISIBLE"));

        actorContext = switchTenant(actor.accessToken(), actor.tenantId());
        Target noRole = createTarget(actor, actorContext, workspaceId, "no-role");
        String noRoleContext = switchTenant(noRole.accessToken(), actor.tenantId());
        request(post("/api/tenants/{tenantId}/data-policies/evaluate", actor.tenantId()),
                evaluationBody, noRoleContext, status().isForbidden());
    }

    private void setColumnPolicy(TenantFixture actor, String actorContext, long fieldId,
            String principalType, long principalId, String mode, String maskConfig) throws Exception {
        String mask = maskConfig == null ? "null" : maskConfig;
        request(post("/api/tenants/{tenantId}/column-policies", actor.tenantId()), """
                {"datasetFieldId":%d,"principalType":"%s","principalId":%d,
                 "accessMode":"%s","maskConfig":%s}
                """.formatted(fieldId, principalType, principalId, mode, mask),
                actorContext, status().isOk());
    }

    private Target createTarget(TenantFixture actor, long workspaceId, String prefix) throws Exception {
        return createTarget(actor, actor.tenantToken(), workspaceId, prefix);
    }

    private Target createTarget(TenantFixture actor, String actorContext,
            long workspaceId, String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = prefix + "-" + suffix + "@example.com";
        JsonNode user = request(post("/api/auth/register"), """
                {"email":"%s","displayName":"数据权限成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        JsonNode member = request(post("/api/tenants/{tenantId}/members", actor.tenantId()), """
                {"userId":%d,"memberName":"数据权限成员"}
                """.formatted(user.path("id").asLong()), actorContext, status().isCreated());
        request(post("/api/tenants/{tenantId}/workspaces/{workspaceId}/members",
                actor.tenantId(), workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(member.path("id").asLong()),
                actorContext, status().isNoContent());
        return new Target(member.path("id").asLong(), login.path("accessToken").asText());
    }

    private String switchTenant(String accessToken, long tenantId) throws Exception {
        return request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk())
                .path("tenantAccessToken").asText();
    }

    private record Target(long memberId, String accessToken) {
    }
}
