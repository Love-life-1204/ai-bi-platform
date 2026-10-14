package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.aibi.platform.identity.service.SecurityAudit;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityChangeAuditRepairTest extends IdentityApiTestSupport {
    @Autowired private SecurityAudit audit;

    @Test
    void roleGrantRecordsActorResourceAndCommittedPermissionVersion() throws Exception {
        var owner = tenantFixture("role_audit");
        long role = request(post("/api/tenants/{t}/roles", owner.tenantId()), "{\"roleCode\":\"reader\",\"roleName\":\"读者\"}",
                owner.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role),
                "{\"permissionId\":7,\"effect\":\"ALLOW\"}", owner.tenantToken(), status().isOk());
        var event = audit.snapshot().stream().filter(e -> e.tenantId()==owner.tenantId() && e.action().equals("ROLE_PERMISSION_CHANGED")).findFirst().orElseThrow();
        assertEquals(owner.userId(), event.userId());
        assertEquals(owner.memberId(), event.memberId());
        assertEquals(role, event.resourceId());
        assertEquals(2, event.permissionVersion());
        assertFalse(event.traceId().isBlank());
        assertEquals("", event.bodyHash());
    }

    @Test
    void aclMutationRecordsActorAndDeniedMutationProducesNoSuccessAudit() throws Exception {
        var owner = tenantFixture("acl_audit");
        var attacker = tenantFixture("audit_other");
        long workspace = request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"audit\",\"workspaceName\":\"审计空间\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        long asset = request(post("/api/tenants/{t}/assets", owner.tenantId()),
                "{\"workspaceId\":"+workspace+",\"assetType\":\"DATASET\",\"assetRefId\":9001}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        String body = "{\"principalType\":\"MEMBER\",\"principalId\":"+owner.memberId()+",\"actionCode\":\"read\",\"effect\":\"DENY\"}";
        request(post("/api/tenants/{t}/assets/{a}/acl", owner.tenantId(), asset), body, attacker.tenantToken(), status().isForbidden());
        assertEquals(0, audit.snapshot().stream().filter(e -> e.tenantId()==owner.tenantId() && e.action().equals("ASSET_ACL_CHANGED")).count());
        request(post("/api/tenants/{t}/assets/{a}/acl", owner.tenantId(), asset), body, owner.tenantToken(), status().isNoContent());
        var events = audit.snapshot().stream().filter(e -> e.tenantId()==owner.tenantId() && e.action().equals("ASSET_ACL_CHANGED")).toList();
        assertEquals(1, events.size());
        assertEquals(owner.memberId(), events.get(0).memberId());
        assertEquals(asset, events.get(0).resourceId());
        assertEquals(2, events.get(0).permissionVersion());
    }
}
