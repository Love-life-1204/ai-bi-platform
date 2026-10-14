package com.aibi.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.identity.mapper.InMemoryWorkspaceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceApiIntegrationTest extends IdentityApiTestSupport {

    @Autowired
    private InMemoryWorkspaceMapper workspaceMapper;

    @Test
    void shouldCreateUpdateAuthorizeAndRevokeWorkspaceMember() throws Exception {
        TenantFixture actor = tenantFixture("workspace");
        JsonNode workspace = request(
                post("/api/tenants/{tenantId}/workspaces", actor.tenantId()), """
                        {"workspaceCode":"finance","workspaceName":"财务空间"}
                        """, actor.tenantToken(), status().isCreated());
        long workspaceId = workspace.path("id").asLong();

        request(put("/api/tenants/{tenantId}/workspaces/{workspaceId}",
                actor.tenantId(), workspaceId), """
                {"workspaceName":"财务分析空间","workspaceStatus":"ACTIVE","lockVersion":0}
                """, actor.tenantToken(), status().isOk());
        request(post("/api/tenants/{tenantId}/workspaces", actor.tenantId()), """
                {"workspaceCode":"finance","workspaceName":"重复空间"}
                """, actor.tenantToken(), status().isConflict());

        String suffix = UUID.randomUUID().toString().replace("-", "");
        JsonNode targetUser = request(post("/api/auth/register"), """
                {"email":"workspace-target-%s@example.com","displayName":"空间成员",
                 "password":"correct-horse-battery-staple"}
                """.formatted(suffix), null, status().isCreated());
        JsonNode targetMember = request(
                post("/api/tenants/{tenantId}/members", actor.tenantId()), """
                        {"userId":%d,"memberName":"空间成员"}
                        """.formatted(targetUser.path("id").asLong()), actor.tenantToken(), status().isCreated());
        long memberId = targetMember.path("id").asLong();

        request(post("/api/tenants/{tenantId}/workspaces/{workspaceId}/members",
                actor.tenantId(), workspaceId), """
                {"memberId":%d,"memberLockVersion":0}
                """.formatted(memberId), actor.tenantToken(), status().isNoContent());
        assertThat(workspaceMapper.isActiveMember(actor.tenantId(), workspaceId, memberId)).isTrue();

        mockMvc.perform(delete("/api/tenants/{tenantId}/workspaces/{workspaceId}/members/{memberId}",
                        actor.tenantId(), workspaceId, memberId)
                        .param("memberLockVersion", "1")
                        .header("Authorization", "Bearer " + actor.tenantToken()))
                .andExpect(status().isNoContent());
        assertThat(workspaceMapper.isActiveMember(actor.tenantId(), workspaceId, memberId)).isFalse();

        mockMvc.perform(delete("/api/tenants/{tenantId}/workspaces/{workspaceId}/members/{memberId}",
                        actor.tenantId(), workspaceId, actor.memberId())
                        .param("memberLockVersion", "0")
                        .header("Authorization", "Bearer " + actor.tenantToken()))
                .andExpect(status().isForbidden());
    }
}
