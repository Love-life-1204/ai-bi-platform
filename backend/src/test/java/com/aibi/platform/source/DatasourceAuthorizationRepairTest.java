package com.aibi.platform.source;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.aibi.platform.identity.IdentityApiTestSupport;
import com.aibi.platform.source.connector.ConnectorRegistry;
import com.aibi.platform.source.connector.ConnectorResult;
import com.aibi.platform.source.connector.SourceConnector;
import com.aibi.platform.source.store.DatasourceStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasourceAuthorizationRepairTest extends IdentityApiTestSupport {

    @MockitoBean
    private ConnectorRegistry connectors;
    @Autowired
    private DatasourceStore sources;
    private SourceConnector connector;

    @BeforeEach
    void isolatedConnector() {
        connector = mock(SourceConnector.class);
        when(connectors.get(any())).thenReturn(connector);
        when(connector.testConnection(any(), any())).thenReturn(new ConnectorResult(1, "fixture"));
    }

    @Test
    void rolelessMemberCannotCreateDatasource() throws Exception {
        TenantFixture owner = tenantFixture("src_deny");
        long workspace = workspace(owner);
        String member = rolelessMember(owner, workspace);
        request(post("/api/datasources").header("X-Tenant-Id", owner.tenantId()),
                body(workspace), member, status().isForbidden());
        assertEquals(0, sources.list(owner.tenantId(), null).size());
    }

    @Test
    void foreignWorkspaceCannotBeUsedByTenantOwner() throws Exception {
        TenantFixture a = tenantFixture("src_a");
        TenantFixture b = tenantFixture("src_b");
        long foreign = workspace(b);
        request(post("/api/datasources").header("X-Tenant-Id", a.tenantId()),
                body(foreign), a.tenantToken(), status().isNotFound());
        assertEquals(0, sources.list(a.tenantId(), null).size());
    }

    @Test
    void rolelessMemberDoesNotSeeDatasourceList() throws Exception {
        TenantFixture owner = tenantFixture("src_list");
        long workspace = workspace(owner);
        String member = rolelessMember(owner, workspace);
        request(post("/api/datasources").header("X-Tenant-Id", owner.tenantId()),
                body(workspace), owner.tenantToken(), status().isCreated());
        JsonNode rows = request(get("/api/datasources").header("X-Tenant-Id", owner.tenantId()),
                null, member, status().isOk());
        assertEquals(0, rows.size());
    }

    @Test
    void deniedConnectionTestNeverOpensExternalConnection() throws Exception {
        TenantFixture owner = tenantFixture("src_test");
        long workspace = workspace(owner);
        String member = rolelessMember(owner, workspace);
        request(post("/api/datasources/connection-tests").header("X-Tenant-Id", owner.tenantId()),
                "{\"workspaceId\":" + workspace + ",\"type\":\"MYSQL\",\"endpoint\":{\"host\":\"db.example.invalid\",\"port\":3306,\"database\":\"sales\",\"tls\":true},\"credential\":{\"username\":\"fake\",\"password\":\"test-only\"}}",
                member, status().isForbidden());
        verify(connector, never()).testConnection(any(), any());
    }

    private long workspace(TenantFixture owner) throws Exception {
        return request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"source_scope\",\"workspaceName\":\"资源范围\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
    }

    private String rolelessMember(TenantFixture owner, long workspace) throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        JsonNode user=request(post("/api/auth/register"), "{\"email\":\""+email+"\",\"displayName\":\"无角色成员\",\"password\":\"correct-horse-battery-staple\"}",null,status().isCreated());
        String global=request(post("/api/auth/login"),"{\"email\":\""+email+"\",\"password\":\"correct-horse-battery-staple\"}",null,status().isOk()).path("accessToken").asText();
        long member=request(post("/api/tenants/{t}/members",owner.tenantId()),"{\"userId\":"+user.path("id").asLong()+",\"memberName\":\"无角色成员\"}",owner.tenantToken(),status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/workspaces/{w}/members",owner.tenantId(),workspace),
                "{\"memberId\":"+member+",\"memberLockVersion\":0}",owner.tenantToken(),status().isNoContent());
        return request(post("/api/auth/switch-tenant"),"{\"tenantId\":"+owner.tenantId()+"}",global,status().isOk()).path("tenantAccessToken").asText();
    }

    private String body(long workspace) {
        return "{\"workspaceId\":"+workspace+",\"code\":\"source_"+UUID.randomUUID().toString().replace("-","")+"\",\"name\":\"隔离数据源\",\"type\":\"MYSQL\",\"endpoint\":{\"host\":\"db.example.invalid\",\"port\":3306,\"database\":\"sales\",\"tls\":true},\"credential\":{\"username\":\"fake\",\"password\":\"test-only\"}}";
    }
}
