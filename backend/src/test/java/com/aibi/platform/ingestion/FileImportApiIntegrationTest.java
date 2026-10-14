package com.aibi.platform.ingestion;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aibi.platform.identity.IdentityApiTestSupport;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.ingestion.storage-dir=target/test-imports")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileImportApiIntegrationTest extends IdentityApiTestSupport {

    @Test
    void shouldUploadPreviewAndImportCsvWithErrorReport() throws Exception {
        TenantFixture fixture = tenantFixture("file_csv");
        long tenantId = fixture.tenantId();
        long workspaceId = workspace(fixture);
        long datasetId = dataset(fixture, workspaceId);
        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv",
                "order_id,amount,order_date\nA001,12.30,2026-10-01\nA002,oops,2026-10-02\n"
                        .getBytes(StandardCharsets.UTF_8));
        MvcResult upload = mockMvc.perform(multipart("/api/files/uploads")
                        .file(file).header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SAFE"))
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(upload.getResponse().getContentAsString()).get("data");
        long fileId = uploaded.get("id").asLong();

        mockMvc.perform(get("/api/files/{id}/content", fileId)
                        .header("X-Tenant-Id", tenantId + 1)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/files/{id}/preview", fileId)
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns", hasSize(3)))
                .andExpect(jsonPath("$.data.columns[2].inferredType").value("DATE"))
                .andExpect(jsonPath("$.data.columns[2].datePatterns[0]").value("yyyy-MM-dd"));

        MvcResult created = mockMvc.perform(post("/api/imports")
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .header("Idempotency-Key", "import-csv-92001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(fileId, workspaceId, datasetId)))
                .andExpect(status().isAccepted())
                .andReturn();
        long importId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        mockMvc.perform(post("/api/imports")
                        .header("X-Tenant-Id", tenantId)
                        .header("Authorization", "Bearer " + fixture.tenantToken())
                        .header("Idempotency-Key", "import-csv-92001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(fileId, workspaceId, datasetId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(importId));
        JsonNode completed = waitForImport(fixture, importId);
        org.junit.jupiter.api.Assertions.assertEquals("PARTIAL_SUCCESS",
                completed.get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, completed.get("totalRows").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(1, completed.get("validRows").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(1, completed.get("errorRows").asLong());
        org.junit.jupiter.api.Assertions.assertTrue(completed.get("errorFileId").isTextual());
        org.junit.jupiter.api.Assertions.assertTrue(completed.get("errorFileId").asText().matches("[1-9][0-9]{0,18}"));
        org.junit.jupiter.api.Assertions.assertTrue(Long.parseLong(completed.get("errorFileId").asText()) > 0);
    }

    @Test
    void shouldRejectWrongExtensionAndBinaryCsv() throws Exception {
        TenantFixture fixture = tenantFixture("file_bad");
        workspace(fixture);
        MockMultipartFile executable = new MockMultipartFile("file", "payload.exe",
                "application/octet-stream", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/files/uploads").file(executable)
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isBadRequest());

        MockMultipartFile binaryCsv = new MockMultipartFile("file", "payload.csv",
                "text/csv", new byte[]{'a', ',', 'b', '\n', 0, 1});
        mockMvc.perform(multipart("/api/files/uploads").file(binaryCsv)
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldParsePlainXlsxAndRejectFormulaCells() throws Exception {
        TenantFixture fixture = tenantFixture("file_xlsx");
        workspace(fixture);
        byte[] plain = workbook(false);
        MockMultipartFile xlsx = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", plain);
        MvcResult result = mockMvc.perform(multipart("/api/files/uploads").file(xlsx)
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isCreated()).andReturn();
        long fileId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        mockMvc.perform(get("/api/files/{id}/preview", fileId)
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sampledRows").value(1));

        MockMultipartFile formula = new MockMultipartFile("file", "formula.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook(true));
        mockMvc.perform(multipart("/api/files/uploads").file(formula)
                        .header("X-Tenant-Id", fixture.tenantId())
                        .header("Authorization", "Bearer " + fixture.tenantToken()))
                .andExpect(status().isBadRequest());
    }

    private long workspace(TenantFixture fixture) throws Exception {
        return request(post("/api/tenants/{t}/workspaces", fixture.tenantId()),
                "{\"workspaceCode\":\"import\",\"workspaceName\":\"导入空间\"}", fixture.tenantToken(), status().isCreated()).path("id").asLong();
    }

    private long dataset(TenantFixture fixture, long workspace) throws Exception {
        return request(post("/api/datasets").header("X-Tenant-Id", fixture.tenantId()),
                "{\"workspaceId\":"+workspace+",\"code\":\"import_target\",\"name\":\"导入目标\",\"fields\":[{\"stableFieldKey\":\"order_id\",\"physicalName\":\"order_id\",\"businessName\":\"编号\",\"dataType\":\"String\",\"role\":\"DIMENSION\",\"sensitivity\":\"PUBLIC\"}]}",
                fixture.tenantToken(), status().isCreated()).path("dataset").path("id").asLong();
    }

    private byte[] workbook(boolean formula) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("订单");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("order_id");
            header.createCell(1).setCellValue("amount");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("A001");
            if (formula) {
                row.createCell(1).setCellFormula("1+1");
            } else {
                row.createCell(1).setCellValue(2);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String importBody(long fileId, long workspaceId, long datasetId) {
        return """
                {"workspaceId":%d,"datasetId":%d,"requesterMemberId":3,"objectFileId":%d,
                 "strictMode":false,"mappings":[
                   {"sourceField":"order_id","targetField":"order_id","targetType":"STRING","required":true},
                   {"sourceField":"amount","targetField":"amount","targetType":"DECIMAL","required":true},
                   {"sourceField":"order_date","targetField":"order_date","targetType":"DATE",
                    "datePattern":"yyyy-MM-dd","required":true}]}
                """.formatted(workspaceId, datasetId, fileId);
    }

    private JsonNode waitForImport(TenantFixture fixture, long importId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/imports/{id}", importId)
                            .header("X-Tenant-Id", fixture.tenantId())
                            .header("Authorization", "Bearer " + fixture.tenantToken()))
                    .andExpect(status().isOk()).andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data");
            if (!java.util.Set.of("PENDING", "RUNNING").contains(data.get("status").asText())) {
                return data;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("导入任务未在预期时间内结束");
    }
}
