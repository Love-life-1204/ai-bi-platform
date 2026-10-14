package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.WorkspaceMapper;
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
class InvitationScopeRepairTest extends IdentityApiTestSupport {

    @Autowired
    private WorkspaceMapper workspaces;
    @Autowired
    private AccessControlMapper access;

    @Test
    void acceptanceAtomicallyAppliesInvitedWorkspaceAndRole() throws Exception {
        TenantFixture owner = tenantFixture("invite_scope");
        long workspace = request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\"scope\",\"workspaceName\":\"邀请范围\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        long role = request(post("/api/tenants/{t}/roles", owner.tenantId()),
                "{\"roleCode\":\"reader\",\"roleName\":\"读者\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role),
                "{\"permissionId\":7,\"effect\":\"ALLOW\"}", owner.tenantToken(), status().isOk());
        String current = switchToken(owner);
        Invitee user = invitee();
        String raw = request(post("/api/tenants/{t}/invitations", owner.tenantId()),
                "{\"email\":\"" + user.email() + "\",\"workspaceId\":" + workspace + ",\"roleId\":" + role
                        + ",\"expiresInHours\":24}", current, status().isCreated()).path("inviteToken").asText();
        JsonNode member = request(post("/api/invitations/accept"),
                "{\"inviteToken\":\"" + raw + "\",\"memberName\":\"受邀读者\"}", user.global(), status().isOk());
        long memberId = member.path("id").asLong();
        assertTrue(workspaces.isActiveMember(owner.tenantId(), workspace, memberId));
        assertEquals(role, access.findMemberRoles(owner.tenantId(), memberId).get(0).roleId());
        assertEquals(workspace, access.findMemberRoles(owner.tenantId(), memberId).get(0).workspaceId().longValue());
        request(post("/api/invitations/accept"), "{\"inviteToken\":\"" + raw
                + "\",\"memberName\":\"重放\"}", user.global(), status().isConflict());
    }

    @Test
    void foreignWorkspaceCannotBeInvitedIntoTargetTenant() throws Exception {
        TenantFixture a = tenantFixture("invite_a");
        TenantFixture b = tenantFixture("invite_b");
        long workspace = request(post("/api/tenants/{t}/workspaces", b.tenantId()),
                "{\"workspaceCode\":\"foreign\",\"workspaceName\":\"异租户\"}", b.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/invitations", a.tenantId()),
                "{\"email\":\"invited@example.com\",\"workspaceId\":" + workspace + ",\"expiresInHours\":24}",
                a.tenantToken(), status().isForbidden());
    }

    @Test
    void revokedInviterCannotKeepGrantingThroughPendingInvitation() throws Exception {
        TenantFixture owner = tenantFixture("invite_revoke");
        Invitee target = invitee();
        String raw = request(post("/api/tenants/{t}/invitations", owner.tenantId()),
                "{\"email\":\"" + target.email() + "\",\"expiresInHours\":24}", owner.tenantToken(), status().isCreated()).path("inviteToken").asText();
        long role = request(post("/api/tenants/{t}/roles", owner.tenantId()),
                "{\"roleCode\":\"deny_members\",\"roleName\":\"撤销邀请权限\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/roles/{r}/permissions", owner.tenantId(), role),
                "{\"permissionId\":2,\"effect\":\"DENY\"}", owner.tenantToken(), status().isOk());
        request(post("/api/tenants/{t}/members/{m}/roles", owner.tenantId(), owner.memberId()),
                "{\"roleId\":" + role + ",\"memberLockVersion\":0}", switchToken(owner), status().isCreated());
        request(post("/api/invitations/accept"), "{\"inviteToken\":\"" + raw
                + "\",\"memberName\":\"被拒绝\"}", target.global(), status().isForbidden());
    }

    private String switchToken(TenantFixture owner) throws Exception {
        return request(post("/api/auth/switch-tenant"), "{\"tenantId\":" + owner.tenantId() + "}",
                owner.accessToken(), status().isOk()).path("tenantAccessToken").asText();
    }

    private Invitee invitee() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        request(post("/api/auth/register"), "{\"email\":\"" + email
                + "\",\"displayName\":\"受邀用户\",\"password\":\"correct-horse-battery-staple\"}", null, status().isCreated());
        String global = request(post("/api/auth/login"), "{\"email\":\"" + email
                + "\",\"password\":\"correct-horse-battery-staple\"}", null, status().isOk()).path("accessToken").asText();
        return new Invitee(email, global);
    }

    private record Invitee(String email, String global) {
    }
}
