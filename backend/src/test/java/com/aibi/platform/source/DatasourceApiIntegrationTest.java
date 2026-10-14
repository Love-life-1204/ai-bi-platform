package com.aibi.platform.source;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aibi.platform.identity.IdentityApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasourceApiIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldCreateListEditAndDeactivateWithoutExposingCredential() throws Exception {
        TenantFixture fixture = tenantFixture("source_api");
        long tenantId = fixture.tenantId();
        long workspace = workspace(fixture);
        String code = "source_" + UUID.randomUUID().toString().replace("-", "");
        mockMvc.perform(get("/api/datasources").header("X-Tenant-Id", tenantId))
                .andExpect(status().isUnauthorized());
        MvcResult createdResult = mockMvc.perform(post("/api/datasources")
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(code, workspace)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.hasCredential").value(true))
                .andExpect(jsonPath("$.data.ownerMemberId").value(fixture.memberId()))
                .andExpect(jsonPath("$.data.credential").doesNotExist())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("data");
        long id = created.get("id").asLong();

        mockMvc.perform(get("/api/datasources").header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].endpoint.host").value("db.example.com"));

        mockMvc.perform(put("/api/datasources/{id}", id)
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"订单库（新）","endpoint":{"host":"db2.example.com","port":3306,
                                "database":"sales","tls":true},"lockVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("订单库（新）"))
                .andExpect(jsonPath("$.data.lockVersion").value(1));

        mockMvc.perform(post("/api/datasources/{id}/deactivate", id)
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .param("lockVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/datasources/{id}/dependencies", id)
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncJobs", hasSize(0)));

        mockMvc.perform(delete("/api/datasources/{id}", id)
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .param("lockVersion", "2"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasources").header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void shouldRejectUnsafeTargetBeforeOpeningConnection() throws Exception {
        TenantFixture fixture = tenantFixture("source_block");
        workspace(fixture);
        mockMvc.perform(post("/api/datasources/connection-tests")
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MYSQL","endpoint":{"host":"127.0.0.1","port":3306,
                                "database":"mysql","tls":false},"credential":{"username":"reader","password":"x"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATASOURCE_TARGET_BLOCKED"));
    }

    private long workspace(TenantFixture fixture) throws Exception {
        return request(post("/api/tenants/{t}/workspaces", fixture.tenantId()),
                "{\"workspaceCode\":\"source\",\"workspaceName\":\"数据源空间\"}", fixture.tenantToken(), status().isCreated())
                .path("id").asLong();
    }

    private String createBody(String code, long workspaceId) {
        return """
                {"workspaceId":%d,"code":"%s","name":"订单库","type":"MYSQL",
                "endpoint":{"host":"db.example.com","port":3306,"database":"sales","tls":true},
                "credential":{"username":"reader","password":"not-returned"}}
                """.formatted(workspaceId, code);
    }
}
