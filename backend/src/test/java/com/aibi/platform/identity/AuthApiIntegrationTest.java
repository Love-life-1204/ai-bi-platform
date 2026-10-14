package com.aibi.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibi.platform.common.api.ApiResponseBodyAdvice;
import com.aibi.platform.common.api.TraceIdFilter;
import com.aibi.platform.common.exception.GlobalExceptionHandler;
import com.aibi.platform.identity.controller.AuthController;
import com.aibi.platform.identity.mapper.InMemoryUserMapper;
import com.aibi.platform.identity.mapper.InMemorySessionMapper;
import com.aibi.platform.identity.mapper.InMemoryMemberMapper;
import com.aibi.platform.identity.security.LoginAttemptLimiter;
import com.aibi.platform.identity.security.PasswordHasher;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.IdentityService;
import com.aibi.platform.identity.service.MfaService;
import com.aibi.platform.identity.service.SessionService;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.manager.TenantManager;
import com.aibi.platform.mapper.InMemoryTenantMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = "app.datasource.enabled=false")
@Import({IdentityService.class, SessionService.class, InMemoryUserMapper.class,
        PermissionContextService.class, InMemorySessionMapper.class, InMemoryMemberMapper.class,
        TenantManager.class, InMemoryTenantMapper.class,
        PasswordHasher.class, TokenService.class, LoginAttemptLimiter.class, MfaService.class,
        GlobalExceptionHandler.class, ApiResponseBodyAdvice.class, TraceIdFilter.class})
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryUserMapper userMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterNormalizedGlobalUserWithBcryptHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Alice@Example.COM","displayName":"Alice",
                                 "password":"correct-horse-battery-staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        var stored = userMapper.findByEmail("alice@example.com").orElseThrow();
        assertThat(stored.passwordHash()).startsWith("$2");
        assertThat(stored.passwordHash()).doesNotContain("correct-horse-battery-staple");
        assertThat(passwordHasher.matches("correct-horse-battery-staple", stored.passwordHash())).isTrue();
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() throws Exception {
        String request = """
                {"email":"duplicate@example.com","displayName":"用户",
                 "password":"correct-horse-battery-staple"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("duplicate", "DUPLICATE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("邮箱已注册"));
    }

    @Test
    void shouldRejectWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak@example.com","displayName":"用户","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldLoginWithPasswordAndIssueSessionTokens() throws Exception {
        register("success@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"SUCCESS@example.com","password":"correct-horse-battery-staple",
                                 "deviceFingerprint":"browser-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("success@example.com"));
    }

    @Test
    void shouldRateLimitRepeatedInvalidPasswords() throws Exception {
        register("limited@example.com");
        String body = """
                {"email":"limited@example.com","password":"definitely-wrong-password"}
                """;
        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank());
    }

    @Test
    void shouldNotRevealWhetherLoginEmailExists() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"definitely-wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录或重新登录"));
    }

    @Test
    void shouldRotateRefreshTokenAndRejectReplay() throws Exception {
        register("rotate@example.com");
        JsonNode login = login("rotate@example.com");
        String oldRefreshToken = login.path("refreshToken").asText();

        String rotatedResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String nextRefreshToken = objectMapper.readTree(rotatedResponse)
                .path("data").path("refreshToken").asText();
        assertThat(nextRefreshToken).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(nextRefreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLogoutAndRevokeAccessAndRefreshTokens() throws Exception {
        register("logout@example.com");
        JsonNode login = login("logout@example.com");
        String accessToken = login.path("accessToken").asText();
        String refreshToken = login.path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"测试用户",
                                 "password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
}
