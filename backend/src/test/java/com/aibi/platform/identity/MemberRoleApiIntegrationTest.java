package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberRoleApiIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldAssignValidateExpiryAndRevokeMemberRole() throws Exception {
        TenantFixture actor = tenantFixture("memberrole");
        JsonNode role = request(post("/api/tenants/{tenantId}/roles", actor.tenantId()), """
                {"roleCode":"analyst","roleName":"分析员"}
                """, actor.tenantToken(), status().isCreated());

        String suffix = UUID.randomUUID().toString().replace("-", "");
        JsonNode targetUser = request(post("/api/auth/register"), """
                {"email":"target-%s@example.com","displayName":"目标成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(suffix), null, status().isCreated());
        JsonNode targetMember = request(
                post("/api/tenants/{tenantId}/members", actor.tenantId()), """
                        {"userId":%d,"memberName":"目标成员"}
                        """.formatted(targetUser.path("id").asLong()), actor.tenantToken(), status().isCreated());
        long memberId = targetMember.path("id").asLong();

        JsonNode assignment = request(
                post("/api/tenants/{tenantId}/members/{memberId}/roles",
                        actor.tenantId(), memberId), """
                        {"roleId":%d,"expiresAt":"%s","memberLockVersion":0}
                        """.formatted(role.path("id").asLong(), Instant.now().plusSeconds(3600)),
                actor.tenantToken(), status().isCreated());

        mockMvc.perform(get("/api/tenants/{tenantId}/members/{memberId}/roles",
                        actor.tenantId(), memberId)
                        .header("Authorization", "Bearer " + actor.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].effective").value(true));

        mockMvc.perform(post("/api/tenants/{tenantId}/members/{memberId}/roles",
                        actor.tenantId(), memberId)
                        .header("Authorization", "Bearer " + actor.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId":%d,"expiresAt":"2020-01-01T00:00:00Z",
                                 "memberLockVersion":1}
                                """.formatted(role.path("id").asLong())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/tenants/{tenantId}/members/{memberId}/roles/{assignmentId}",
                        actor.tenantId(), memberId, assignment.path("id").asLong())
                        .param("memberLockVersion", "1")
                        .header("Authorization", "Bearer " + actor.tenantToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tenants/{tenantId}/members/{memberId}/roles",
                        actor.tenantId(), memberId)
                        .header("Authorization", "Bearer " + actor.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
