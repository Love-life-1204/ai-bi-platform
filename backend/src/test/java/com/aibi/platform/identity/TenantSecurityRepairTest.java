package com.aibi.platform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantSecurityRepairTest extends IdentityApiTestSupport {

    private long registeredUserId;

    @Test
    void anonymousOnboardingMustBeRejected() throws Exception {
        request(post("/api/tenants"), tenantBody(), null, status().isUnauthorized());
    }

    @Test
    void anonymousTenantReadMustBeRejectedOnBothAliases() throws Exception {
        JsonNode tenant = createWithGlobalSession();
        long id = tenant.path("id").asLong();
        request(get("/api/tenants/{id}", id), null, null, status().isUnauthorized());
        request(get("/api/v1/tenants/{id}", id), null, null, status().isUnauthorized());
    }

    @Test
    void anonymousTenantMutationMustBeRejected() throws Exception {
        long id = createWithGlobalSession().path("id").asLong();
        request(put("/api/tenants/{id}", id), updateBody(), null, status().isUnauthorized());
        request(post("/api/tenants/{id}/freeze", id), "{\"lockVersion\":0}", null,
                status().isUnauthorized());
    }

    @Test
    void anonymousMemberAdditionMustBeRejected() throws Exception {
        String global = globalSession();
        long id = request(post("/api/tenants"), tenantBody(), global, status().isCreated()).path("id").asLong();
        String attacker = globalSession();
        long userId = registeredUserId;
        request(post("/api/tenants/{id}/members", id),
                "{\"userId\":" + Math.max(1, userId) + ",\"memberName\":\"攻击成员\"}",
                null, status().isUnauthorized());
        request(post("/api/auth/switch-tenant"), "{\"tenantId\":" + id + "}", attacker, status().isForbidden());
        assertEquals(0, request(get("/api/users/me/memberships"), null, attacker, status().isOk()).size());
    }

    @Test
    void onboardingMustBindAuthenticatedOwner() throws Exception {
        String global = globalSession();
        JsonNode tenant = request(post("/api/tenants"), tenantBody(), global, status().isCreated());
        JsonNode memberships = request(get("/api/users/me/memberships"), null, global, status().isOk());
        assertEquals(1, memberships.size());
        assertEquals(tenant.path("id").asLong(), memberships.get(0).path("tenantId").asLong());
        assertTrue(memberships.get(0).path("owner").asBoolean());
    }

    @Test
    void tenantTokenCannotReadOrChangeAnotherTenant() throws Exception {
        TenantFixture a = tenantFixture("sec_a");
        TenantFixture b = tenantFixture("sec_b");
        request(get("/api/tenants/{id}", b.tenantId()), null, a.tenantToken(), status().isForbidden());
        request(put("/api/tenants/{id}", b.tenantId()), updateBody(), a.tenantToken(), status().isForbidden());
    }

    @Test
    void onboardingIdempotencyKeyReplaysOnlyTheSameAuthenticatedRequest() throws Exception {
        String global = globalSession();
        String body = tenantBody();
        String key = "onboarding-" + UUID.randomUUID();
        JsonNode first = request(post("/api/tenants").header("Idempotency-Key", key), body,
                global, status().isCreated());
        JsonNode replay = request(post("/api/tenants").header("Idempotency-Key", key), body,
                global, status().isCreated());
        assertEquals(first.path("id").asLong(), replay.path("id").asLong());
        request(post("/api/tenants").header("Idempotency-Key", key),
                body.replace("安全测试企业", "内容被改变"), global, status().isConflict());
        assertEquals(1, request(get("/api/users/me/memberships"), null, global, status().isOk()).size());
    }

    private JsonNode createWithGlobalSession() throws Exception {
        return request(post("/api/tenants"), tenantBody(), globalSession(), status().isCreated());
    }

    private String globalSession() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.com";
        registeredUserId = request(post("/api/auth/register"), "{\"email\":\"" + email
                + "\",\"displayName\":\"安全测试\",\"password\":\"correct-horse-battery-staple\"}",
                null, status().isCreated()).path("id").asLong();
        return request(post("/api/auth/login"), "{\"email\":\"" + email
                + "\",\"password\":\"correct-horse-battery-staple\"}", null, status().isOk())
                .path("accessToken").asText();
    }

    private String tenantBody() {
        return "{\"tenantCode\":\"sec_" + UUID.randomUUID().toString().replace("-", "")
                + "\",\"tenantName\":\"安全测试企业\"}";
    }

    private String updateBody() {
        return "{\"tenantName\":\"未授权修改\",\"timezone\":\"UTC\",\"defaultCurrency\":\"USD\",\"lockVersion\":0}";
    }
}
