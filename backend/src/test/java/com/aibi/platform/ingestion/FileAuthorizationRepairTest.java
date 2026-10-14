package com.aibi.platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.identity.IdentityApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.aibi.platform.ingestion.manager.ImportWorkerManager;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import com.aibi.platform.ingestion.service.IngestionServiceImpl;
import com.aibi.platform.ingestion.manager.FileImportManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = "app.ingestion.storage-dir=target/test-file-authorization")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileAuthorizationRepairTest extends IdentityApiTestSupport {

    @Autowired
    private AssetAccessMapper assets;

    @Autowired
    private IngestionMapper ingestion;

    @MockitoBean
    private ImportWorkerManager importWorker;

    @Test
    void actualAdminAnalystViewerAndRolelessMembersFollowResourceMatrix() throws Exception {
        for (String code : new String[]{"admin", "analyst", "viewer", "none"}) {
            var owner = tenantFixture("matrix_"+code);
            long workspace = workspace(owner);
            long dataset = dataset(owner, workspace);
            long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
            var member = account(owner, workspace);
            String token = member.tenantToken();
            if (!code.equals("none")) {
                long role = request(post("/api/tenants/{t}/roles", owner.tenantId()),
                        "{\"roleCode\":\""+code+"\",\"roleName\":\"矩阵角色\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
                request(post("/api/tenants/{t}/members/{m}/roles", owner.tenantId(), member.memberId()),
                        "{\"roleId\":"+role+",\"workspaceId\":"+workspace+",\"memberLockVersion\":1}", owner.tenantToken(), status().isCreated());
                token = request(post("/api/auth/switch-tenant"), "{\"tenantId\":"+owner.tenantId()+"}", member.globalToken(), status().isOk()).path("tenantAccessToken").asText();
            }
            boolean canImport = code.equals("admin") || code.equals("analyst");
            upload(owner, workspace, token, canImport ? status().isCreated() : status().isForbidden());
            long source = request(post("/api/datasources").header("X-Tenant-Id", owner.tenantId()), sourceBody(workspace),
                    owner.tenantToken(), status().isCreated()).path("id").asLong();
            request(post("/api/datasources").header("X-Tenant-Id", owner.tenantId()), sourceBody(workspace), token,
                    code.equals("admin") ? status().isCreated() : status().isForbidden());
            assertEquals(code.equals("admin") ? 2 : code.equals("analyst") ? 1 : 0,
                    request(get("/api/datasources").header("X-Tenant-Id", owner.tenantId()), null, token, status().isOk()).size());
            var imported = request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "matrix-import"),
                    importBody(workspace, dataset, file), token, canImport ? status().isAccepted() : status().isForbidden());
            if (canImport) {
                assertEquals(member.memberId(), ingestion.findImport(owner.tenantId(), imported.path("id").asLong()).orElseThrow().requesterMemberId());
            } else {
                assertEquals(0, ingestion.listImports(owner.tenantId()).size());
            }
            String sync = "{\"datasourceId\":"+source+",\"datasetId\":"+dataset+",\"jobCode\":\"matrix_sync\",\"syncMode\":\"FULL\",\"sourceSchema\":\"sales\",\"sourceTable\":\"orders\",\"primaryKeyField\":\"id\",\"batchSize\":100,\"timezone\":\"UTC\",\"enabled\":true,\"mappings\":[{\"sourceField\":\"id\",\"targetField\":\"id\",\"targetType\":\"STRING\",\"required\":true}]}";
            request(post("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()), sync, token,
                    code.equals("admin") ? status().isCreated() : status().isForbidden());
        }
    }

    private String sourceBody(long workspace) {
        return "{\"workspaceId\":"+workspace+",\"code\":\"matrix_"+UUID.randomUUID().toString().replace("-", "")+"\",\"name\":\"矩阵数据源\",\"type\":\"MYSQL\",\"endpoint\":{\"host\":\"db.example.invalid\",\"port\":3306,\"database\":\"sales\",\"tls\":true},\"credential\":{\"username\":\"fake\",\"password\":\"test-only\"}}";
    }

    @Autowired
    private IngestionServiceImpl service;

    @MockitoSpyBean
    private FileImportManager files;

    @Test
    void queuedImportStopsBeforeFileReadWhenAccessIsRevoked() throws Exception {
        var owner = tenantFixture("queued_import");
        long workspace = workspace(owner);
        long dataset = dataset(owner, workspace);
        long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
        long job = request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "queued-before-revoke"),
                importBody(workspace, dataset, file), owner.tenantToken(), status().isAccepted()).path("id").asLong();
        assertEquals(owner.memberId(), ingestion.findImport(owner.tenantId(), job).orElseThrow().requesterMemberId());
        denyFileRead(owner, file);
        org.junit.jupiter.api.Assertions.assertThrows(com.aibi.platform.common.exception.ForbiddenException.class,
                () -> service.consumeImport(owner.tenantId(), job));
        verify(files, never()).parse(any());
        assertEquals("FAILED", ingestion.findImport(owner.tenantId(), job).orElseThrow().status());
        org.junit.jupiter.api.Assertions.assertNull(ingestion.publication(owner.tenantId(), dataset).activeGeneration());
    }

    @Test
    void revocationDuringParsePreventsDataPublication() throws Exception {
        var owner = tenantFixture("running_import");
        long workspace = workspace(owner);
        long dataset = dataset(owner, workspace);
        long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
        long job = request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "running-before-revoke"),
                importBody(workspace, dataset, file), owner.tenantToken(), status().isAccepted()).path("id").asLong();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            denyFileRead(owner, file);
            return result;
        }).when(files).parse(any());
        org.junit.jupiter.api.Assertions.assertThrows(com.aibi.platform.common.exception.ForbiddenException.class,
                () -> service.consumeImport(owner.tenantId(), job));
        assertEquals("FAILED", ingestion.findImport(owner.tenantId(), job).orElseThrow().status());
        org.junit.jupiter.api.Assertions.assertNull(ingestion.publication(owner.tenantId(), dataset).activeGeneration());
        assertEquals(0, ingestion.findImport(owner.tenantId(), job).orElseThrow().validRows());
    }

    private void denyFileRead(TenantFixture owner, long file) throws Exception {
        long asset = assets.findAssetByReference(owner.tenantId(), "FILE", file).orElseThrow().id();
        request(post("/api/tenants/{t}/assets/{a}/acl", owner.tenantId(), asset),
                "{\"principalType\":\"MEMBER\",\"principalId\":"+owner.memberId()+",\"actionCode\":\"read\",\"effect\":\"DENY\"}",
                owner.tenantToken(), status().isNoContent());
    }

    @Test
    void rolelessMemberCannotSubmitImportOrSeeOtherMembersJobs() throws Exception {
        TenantFixture owner = tenantFixture("import_role");
        long workspace = workspace(owner);
        long dataset = dataset(owner, workspace);
        long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
        String member = roleless(owner, workspace);
        String body = importBody(workspace, dataset, file);
        request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "denied-import"),
                body, member, status().isForbidden());
        assertEquals(0, ingestion.listImports(owner.tenantId()).size());
        request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "owner-import"),
                body, owner.tenantToken(), status().isAccepted());
        assertEquals(0, request(get("/api/imports").header("X-Tenant-Id", owner.tenantId()),
                null, member, status().isOk()).size());
    }

    @Test
    void importCannotCombineFileAndDatasetFromDifferentWorkspaces() throws Exception {
        TenantFixture owner = tenantFixture("import_scope");
        long first = workspace(owner);
        long second = request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"other\",\"workspaceName\":\"另一空间\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        long dataset = dataset(owner, second);
        long file = upload(owner, first, owner.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/imports").header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "scope-import"),
                importBody(second, dataset, file), owner.tenantToken(), status().isNotFound());
        assertEquals(0, ingestion.listImports(owner.tenantId()).size());
    }

    private long dataset(TenantFixture owner, long workspace) throws Exception {
        return request(post("/api/datasets").header("X-Tenant-Id", owner.tenantId()),
                "{\"workspaceId\":"+workspace+",\"code\":\"file_target\",\"name\":\"导入目标\",\"fields\":[{\"stableFieldKey\":\"id\",\"physicalName\":\"id\",\"businessName\":\"编号\",\"dataType\":\"String\",\"role\":\"DIMENSION\",\"sensitivity\":\"PUBLIC\"}]}",
                owner.tenantToken(), status().isCreated()).path("dataset").path("id").asLong();
    }

    private String importBody(long workspace, long dataset, long file) {
        return "{\"workspaceId\":"+workspace+",\"datasetId\":"+dataset+",\"objectFileId\":"+file+",\"requesterMemberId\":999,\"strictMode\":false,\"mappings\":[{\"sourceField\":\"id\",\"targetField\":\"id\",\"targetType\":\"STRING\",\"required\":true}]}";
    }

    @Test
    void explicitAclDenyOverridesFileOwnerAndRole() throws Exception {
        TenantFixture owner = tenantFixture("file_acl");
        long workspace = workspace(owner);
        long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
        var asset = assets.findAssetByReference(owner.tenantId(), "FILE", file).orElseThrow();
        request(post("/api/tenants/{t}/assets/{a}/acl", owner.tenantId(), asset.id()),
                "{\"principalType\":\"MEMBER\",\"principalId\":" + owner.memberId() + ",\"actionCode\":\"read\",\"effect\":\"DENY\"}",
                owner.tenantToken(), status().isNoContent());
        String refreshed = request(post("/api/auth/switch-tenant"), "{\"tenantId\":" + owner.tenantId() + "}",
                owner.accessToken(), status().isOk()).path("tenantAccessToken").asText();
        request(get("/api/files/{id}/preview", file).header("X-Tenant-Id", owner.tenantId()),
                null, refreshed, status().isNotFound());
        // Denying preview must not incorrectly deny the separate download action.
        mockMvc.perform(get("/api/files/{id}/content", file).header("X-Tenant-Id", owner.tenantId())
                .header("Authorization", "Bearer " + refreshed)).andExpect(status().isOk());
    }

    @Test
    void rolelessMemberCannotUploadEvenInsideOwnWorkspace() throws Exception {
        TenantFixture owner = tenantFixture("file_role");
        long workspace = workspace(owner);
        String member = roleless(owner, workspace);
        upload(owner, workspace, member, status().isForbidden());
    }

    @Test
    void foreignWorkspaceCannotReceiveFiles() throws Exception {
        TenantFixture a = tenantFixture("file_a");
        TenantFixture b = tenantFixture("file_b");
        upload(a, workspace(b), a.tenantToken(), status().isNotFound());
    }

    @Test
    void rolelessMemberCannotPreviewOrDownloadOtherMembersFile() throws Exception {
        TenantFixture owner = tenantFixture("file_read");
        long workspace = workspace(owner);
        long file = upload(owner, workspace, owner.tenantToken(), status().isCreated()).path("id").asLong();
        String member = roleless(owner, workspace);
        request(get("/api/files/{id}/preview", file).header("X-Tenant-Id", owner.tenantId()),
                null, member, status().isForbidden());
        mockMvc.perform(get("/api/files/{id}/content", file).header("X-Tenant-Id", owner.tenantId())
                .header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
    }

    @Test
    void uploadPersistsTrustedOwnerAndWorkspaceAndOwnerCanPreview() throws Exception {
        TenantFixture owner = tenantFixture("file_scope");
        long workspace = workspace(owner);
        JsonNode file = upload(owner, workspace, owner.tenantToken(), status().isCreated());
        assertEquals(workspace, file.path("workspaceId").asLong());
        assertEquals(owner.memberId(), file.path("ownerMemberId").asLong());
        request(get("/api/files/{id}/preview", file.path("id").asLong()).header("X-Tenant-Id", owner.tenantId()),
                null, owner.tenantToken(), status().isOk());
    }

    private JsonNode upload(TenantFixture owner, long workspace, String token,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        var file = new MockMultipartFile("file", "scope.csv", "text/csv", "id,amount\nA,1\n".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/files/uploads").file(file)
                .param("workspaceId", Long.toString(workspace)).header("X-Tenant-Id", owner.tenantId())
                .header("Authorization", "Bearer " + token)).andExpect(expected)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private long workspace(TenantFixture owner) throws Exception {
        return request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"file_scope\",\"workspaceName\":\"文件范围\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
    }

    private String roleless(TenantFixture owner, long workspace) throws Exception {
        return account(owner, workspace).tenantToken();
    }

    private record MemberFixture(long memberId, String globalToken, String tenantToken) { }

    private MemberFixture account(TenantFixture owner, long workspace) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        JsonNode user = request(post("/api/auth/register"), "{\"email\":\""+email+"\",\"displayName\":\"无角色\",\"password\":\"correct-horse-battery-staple\"}", null, status().isCreated());
        String global = request(post("/api/auth/login"), "{\"email\":\""+email+"\",\"password\":\"correct-horse-battery-staple\"}", null, status().isOk()).path("accessToken").asText();
        long member = request(post("/api/tenants/{t}/members", owner.tenantId()), "{\"userId\":"+user.path("id").asLong()+",\"memberName\":\"无角色\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/workspaces/{w}/members", owner.tenantId(), workspace), "{\"memberId\":"+member+",\"memberLockVersion\":0}", owner.tenantToken(), status().isNoContent());
        String token = request(post("/api/auth/switch-tenant"), "{\"tenantId\":"+owner.tenantId()+"}", global, status().isOk()).path("tenantAccessToken").asText();
        return new MemberFixture(member, global, token);
    }
}
