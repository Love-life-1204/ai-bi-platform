package com.aibi.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.identity.mapper.InMemoryInvitationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitationApiIntegrationTest extends IdentityApiTestSupport {

    @Autowired
    private InMemoryInvitationMapper invitationMapper;

    @Autowired
    private com.aibi.platform.identity.security.TokenService tokenService;

    @Test
    void shouldCreateSingleExposureInvitationTokenAndStoreOnlyHash() throws Exception {
        TenantFixture fixture = tenantFixture("invite");
        var invitation = request(
                post("/api/tenants/{tenantId}/invitations", fixture.tenantId()), """
                        {"email":"Invited@Example.COM","expiresInHours":24}
                        """, fixture.tenantToken(), status().isCreated());

        String rawToken = invitation.path("inviteToken").asText();
        assertThat(rawToken).isNotBlank();
        var stored = invitationMapper.findByTokenHash(tokenService.hash(rawToken)).orElseThrow();
        assertThat(stored.email()).isEqualTo("invited@example.com");
        assertThat(stored.tokenHash()).doesNotContain(rawToken);
        assertThat(stored.invitationStatus()).isEqualTo("PENDING");
    }
}
