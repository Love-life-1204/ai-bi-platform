package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiTenantMembershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAssociateOneGlobalUserWithMultipleTenants() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        JsonNode user = register("multi-" + suffix + "@example.com");
        String accessToken = login(user.path("email").asText());
        long firstTenant = createTenant("first_" + suffix, accessToken);
        long secondTenant = createTenant("second_" + suffix, accessToken);
        JsonNode unrelatedOwner = register("unrelated-owner-" + suffix + "@example.com");
        long unrelatedTenant = createTenant("unrelated_" + suffix, login(unrelatedOwner.path("email").asText()));


        mockMvc.perform(get("/api/users/me/memberships")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(user.path("id").asLong()))
                .andExpect(jsonPath("$.data[1].userId").value(user.path("id").asLong()));

        mockMvc.perform(post("/api/tenants/{tenantId}/members", firstTenant)
                        .header("Authorization", "Bearer " + tenantToken(accessToken, firstTenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"memberName":"重复成员"}
                                """.formatted(user.path("id").asLong())))
                .andExpect(status().isConflict());

        switchTenant(accessToken, firstTenant, firstTenant);
        String firstTenantToken = tenantToken(accessToken, firstTenant);
        mockMvc.perform(get("/api/tenants/{tenantId}/members", firstTenant)
                        .header("Authorization", "Bearer " + firstTenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].memberName").value("跨租户用户"));
        switchTenant(accessToken, secondTenant, secondTenant);
        mockMvc.perform(post("/api/auth/switch-tenant")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d}
                                """.formatted(unrelatedTenant)))
                .andExpect(status().isForbidden());
    }

    private JsonNode register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"跨租户用户",
                                 "password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private long createTenant(String tenantCode, String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"测试企业"}
                                """.formatted(tenantCode)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void addMember(long tenantId, long userId, String memberName) throws Exception {
        mockMvc.perform(post("/api/tenants/{tenantId}/members", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"memberName":"%s"}
                                """.formatted(userId, memberName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(tenantId));
    }

    private void switchTenant(String accessToken, long tenantId, long expectedTenantId) throws Exception {
        mockMvc.perform(post("/api/auth/switch-tenant")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d}
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(expectedTenantId))
                .andExpect(jsonPath("$.data.tenantAccessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tenantPermissionVersion").value(1))
                .andExpect(jsonPath("$.data.memberPermissionVersion").value(1));
    }

    private String tenantToken(String accessToken, long tenantId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/switch-tenant")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d}
                                """.formatted(tenantId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("tenantAccessToken").asText();
    }
}
