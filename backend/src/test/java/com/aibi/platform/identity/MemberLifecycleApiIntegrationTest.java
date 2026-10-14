package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MemberLifecycleApiIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldJoinOnceThenDisableAndRemoveMember() throws Exception {
        TenantFixture inviter = tenantFixture("memberlife");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String inviteeEmail = "invitee-" + suffix + "@example.com";
        request(post("/api/auth/register"), """
                {"email":"%s","displayName":"受邀用户","password":"correct-horse-battery-staple"}
                """.formatted(inviteeEmail), null, status().isCreated());
        JsonNode login = request(post("/api/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(inviteeEmail), null, status().isOk());
        String inviteeAccessToken = login.path("accessToken").asText();

        JsonNode invitation = request(
                post("/api/tenants/{tenantId}/invitations", inviter.tenantId()), """
                        {"email":"%s","expiresInHours":24}
                        """.formatted(inviteeEmail), inviter.tenantToken(), status().isCreated());
        String inviteToken = invitation.path("inviteToken").asText();
        JsonNode member = request(post("/api/invitations/accept"), """
                {"inviteToken":"%s","memberName":"受邀成员"}
                """.formatted(inviteToken), inviteeAccessToken, status().isOk());
        long memberId = member.path("id").asLong();

        request(post("/api/invitations/accept"), """
                {"inviteToken":"%s","memberName":"重放"}
                """.formatted(inviteToken), inviteeAccessToken, status().isConflict());

        JsonNode switched = request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(inviter.tenantId()), inviteeAccessToken, status().isOk());
        String oldTenantToken = switched.path("tenantAccessToken").asText();

        request(post("/api/tenants/{tenantId}/members/{memberId}/disable",
                inviter.tenantId(), memberId), """
                {"lockVersion":0}
                """, inviter.tenantToken(), status().isOk());

        mockMvc.perform(get("/api/tenants/{tenantId}/departments/tree", inviter.tenantId())
                        .header("Authorization", "Bearer " + oldTenantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tenants/{tenantId}/members/{memberId}",
                        inviter.tenantId(), memberId)
                        .param("lockVersion", "1")
                        .header("Authorization", "Bearer " + inviter.tenantToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/switch-tenant")
                        .header("Authorization", "Bearer " + inviteeAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d}
                                """.formatted(inviter.tenantId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me/memberships")
                        .header("Authorization", "Bearer " + inviteeAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
