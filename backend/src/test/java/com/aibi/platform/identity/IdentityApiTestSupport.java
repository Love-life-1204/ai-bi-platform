package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public abstract class IdentityApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected TenantFixture tenantFixture(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = prefix + "-" + suffix + "@example.com";
        JsonNode user = request(post("/api/auth/register"), """
                {"email":"%s","displayName":"测试成员","password":"correct-horse-battery-staple"}
                """.formatted(email), null, MockMvcResultMatchers.status().isCreated());
        JsonNode login = request(post("/api/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, MockMvcResultMatchers.status().isOk());
        String accessToken = login.path("accessToken").asText();
        JsonNode tenant = request(post("/api/tenants"), """
                {"tenantCode":"%s_%s","tenantName":"测试企业"}
                """.formatted(prefix, suffix), accessToken, MockMvcResultMatchers.status().isCreated());
        long tenantId = tenant.path("id").asLong();
        JsonNode member = request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/users/me/memberships"), null, accessToken,
                MockMvcResultMatchers.status().isOk()).get(0);
        JsonNode context = request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, MockMvcResultMatchers.status().isOk());
        return new TenantFixture(
                user.path("id").asLong(), email, accessToken, tenantId,
                member.path("id").asLong(), context.path("tenantAccessToken").asText());
    }

    protected JsonNode request(MockHttpServletRequestBuilder builder, String body,
            String bearerToken, ResultMatcher expectedStatus) throws Exception {
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        String response = mockMvc.perform(builder).andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(response).path("data");
    }

    protected record TenantFixture(
            long userId,
            String email,
            String accessToken,
            long tenantId,
            long memberId,
            String tenantToken) {
    }
}
