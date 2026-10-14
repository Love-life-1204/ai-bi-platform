package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class DepartmentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateUpdateAndRenderDepartmentHierarchyWithoutCycles() throws Exception {
        Fixture fixture = fixture();
        long rootId = createDepartment(fixture, null, "head", "总部", 0);
        long childId = createDepartment(fixture, rootId, "sales", "销售部", 10);

        mockMvc.perform(get("/api/tenants/{tenantId}/departments/tree", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].departmentCode").value("head"))
                .andExpect(jsonPath("$.data[0].children[0].departmentCode").value("sales"));

        mockMvc.perform(put("/api/tenants/{tenantId}/departments/{departmentId}",
                        fixture.tenantId(), rootId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"departmentName":"总部","sortOrder":0,"lockVersion":0}
                                """.formatted(childId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(put("/api/tenants/{tenantId}/departments/{departmentId}",
                        fixture.tenantId(), childId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":null,"departmentName":"销售中心","sortOrder":5,"lockVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentName").value("销售中心"))
                .andExpect(jsonPath("$.data.lockVersion").value(1));
    }

    private Fixture fixture() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "department-" + suffix + "@example.com";
        JsonNode user = request(post("/api/auth/register"), """
                {"email":"%s","displayName":"部门管理员","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isCreated());
        JsonNode login = request(post("/api/auth/login"), """
                {"email":"%s","password":"correct-horse-battery-staple"}
                """.formatted(email), null, status().isOk());
        String accessToken = login.path("accessToken").asText();
        JsonNode tenant = request(post("/api/tenants"), """
                {"tenantCode":"dept_%s","tenantName":"部门测试企业"}
                """.formatted(suffix), accessToken, status().isCreated());
        long tenantId = tenant.path("id").asLong();
        JsonNode context = request(post("/api/auth/switch-tenant"), """
                {"tenantId":%d}
                """.formatted(tenantId), accessToken, status().isOk());
        return new Fixture(tenantId, context.path("tenantAccessToken").asText());
    }

    private long createDepartment(Fixture fixture, Long parentId, String code,
            String name, int sortOrder) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        JsonNode result = request(post("/api/tenants/{tenantId}/departments", fixture.tenantId()), """
                {"parentId":%s,"departmentCode":"%s","departmentName":"%s","sortOrder":%d}
                """.formatted(parent, code, name, sortOrder), fixture.tenantToken(), status().isCreated());
        return result.path("id").asLong();
    }

    private JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String body, String bearerToken,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        String response = mockMvc.perform(builder).andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private record Fixture(long tenantId, String tenantToken) {
    }
}
