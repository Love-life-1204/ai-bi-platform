package com.aibi.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aibi.platform.identity.IdentityApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantApiIntegrationTest extends IdentityApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String globalToken;
    private String tenantToken;

    @BeforeEach
    void authenticate() throws Exception {
        TenantFixture owner = tenantFixture("tenant_api");
        globalToken = owner.accessToken();
        tenantToken = owner.tenantToken();
    }

    @Test
    void shouldCreateTenantWithDefaults() throws Exception {
        String tenantCode = uniqueTenantCode();

        mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"示例企业"}
                                """.formatted(tenantCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.matchesPattern("[1-9][0-9]{0,18}")))
                .andExpect(jsonPath("$.data.tenantCode").value(tenantCode))
                .andExpect(jsonPath("$.data.tenantName").value("示例企业"))
                .andExpect(jsonPath("$.data.tenantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.defaultCurrency").value("CNY"))
                .andExpect(jsonPath("$.data.permissionVersion").value(1))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lockVersion").value(0))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void shouldRejectDuplicateTenantCode() throws Exception {
        String tenantCode = uniqueTenantCode();
        String requestBody = """
                {"tenantCode":"%s","tenantName":"首个企业"}
                """.formatted(tenantCode);

        mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"重复企业"}
                                """.formatted(tenantCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("租户编码已存在"));
    }

    @Test
    void shouldValidateTenantCodeAndTimezone() throws Exception {
        mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"Invalid-Code","tenantName":"示例企业"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("tenantCode"));

        mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"示例企业","timezone":"Mars/Olympus"}
                                """.formatted(uniqueTenantCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("时区标识不合法"));
    }

    @Test
    void shouldQueryAndUpdateTenantBasicInfo() throws Exception {
        String tenantCode = uniqueTenantCode();
        JsonNode created = createTenant(tenantCode, "修改前企业");
        long tenantId = created.path("id").asLong();

        mockMvc.perform(get("/api/tenants/{tenantId}", tenantId).header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantCode").value(tenantCode))
                .andExpect(jsonPath("$.data.tenantName").value("修改前企业"))
                .andExpect(jsonPath("$.data.lockVersion").value(0));

        mockMvc.perform(put("/api/tenants/{tenantId}", tenantId).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantName":"修改后企业","timezone":"UTC",
                                 "defaultCurrency":"USD","lockVersion":0,"tenantStatus":"FROZEN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantCode").value(tenantCode))
                .andExpect(jsonPath("$.data.tenantName").value("修改后企业"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"))
                .andExpect(jsonPath("$.data.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.data.tenantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lockVersion").value(1));
    }

    @Test
    void shouldRejectMissingTenantAndStaleUpdate() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}", Long.MAX_VALUE).header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        JsonNode created = createTenant(uniqueTenantCode(), "并发更新企业");
        long tenantId = created.path("id").asLong();
        String updateBody = """
                {"tenantName":"首次修改","timezone":"Asia/Shanghai",
                 "defaultCurrency":"CNY","lockVersion":0}
                """;
        mockMvc.perform(put("/api/tenants/{tenantId}", tenantId).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tenants/{tenantId}", tenantId).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void shouldValidateUpdatedTimezone() throws Exception {
        JsonNode created = createTenant(uniqueTenantCode(), "时区校验企业");

        mockMvc.perform(put("/api/tenants/{tenantId}", created.path("id").asLong()).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantName":"时区校验企业","timezone":"Mars/Olympus",
                                 "defaultCurrency":"CNY","lockVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("时区标识不合法"));
    }

    @Test
    void shouldFreezeAndUnfreezeTenantWithValidTransitions() throws Exception {
        JsonNode created = createTenant(uniqueTenantCode(), "状态管理企业");
        long tenantId = created.path("id").asLong();

        mockMvc.perform(post("/api/tenants/{tenantId}/freeze", tenantId).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lockVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantStatus").value("FROZEN"))
                .andExpect(jsonPath("$.data.lockVersion").value(1));

        mockMvc.perform(post("/api/tenants/{tenantId}/freeze", tenantId).header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lockVersion":1}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/tenants/{tenantId}/unfreeze", tenantId).header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lockVersion":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lockVersion").value(2));
    }

    private JsonNode createTenant(String tenantCode, String tenantName) throws Exception {
        String response = mockMvc.perform(post("/api/tenants")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"%s"}
                                """.formatted(tenantCode, tenantName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response).path("data");
        tenantToken = request(post("/api/auth/switch-tenant"),
                "{\"tenantId\":" + created.path("id").asLong() + "}", globalToken, status().isOk())
                .path("tenantAccessToken").asText();
        return created;
    }

    private String uniqueTenantCode() {
        return "tenant_" + UUID.randomUUID().toString().replace("-", "");
    }
}
