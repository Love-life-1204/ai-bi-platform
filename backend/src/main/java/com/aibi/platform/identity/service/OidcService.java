package com.aibi.platform.identity.service;

import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcCallbackRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcConfigRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcStartData;
import com.aibi.platform.commercial.model.CommercialModels.OidcAuthorization;
import com.aibi.platform.commercial.model.CommercialModels.OidcProvider;
import com.aibi.platform.commercial.store.CommercialStore;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.exception.UnauthorizedException;
import com.aibi.platform.identity.dto.LoginData;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OidcService {

    private final PermissionContextService permissions;
    private final CommercialStore store;
    private final CommercialProperties properties;
    private final UserMapper users;
    private final MemberMapper members;
    private final SessionService sessions;
    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();

    public OidcService(PermissionContextService permissions, CommercialStore store,
            CommercialProperties properties, UserMapper users, MemberMapper members,
            SessionService sessions,
            RestClient.Builder restClientBuilder) {
        this.permissions = permissions;
        this.store = store;
        this.properties = properties;
        this.users = users;
        this.members = members;
        this.sessions = sessions;
        this.restClient = restClientBuilder.build();
    }

    public OidcConfigRequest configure(String authorization, long tenantId,
            OidcConfigRequest request) {
        requireOwner(authorization, tenantId);
        URI issuer = secureUri(request.issuer());
        sameOrigin(issuer, secureUri(request.authorizationEndpoint()));
        sameOrigin(issuer, secureUri(request.tokenEndpoint()));
        sameOrigin(issuer, secureUri(request.jwksUri()));
        properties.oidcSecret(request.clientSecretRef());
        OidcProvider provider = new OidcProvider(tenantId, issuer.toString(), request.clientId(),
                request.clientSecretRef(), request.authorizationEndpoint(), request.tokenEndpoint(),
                request.jwksUri(), normalizeDomain(request.emailDomain()), request.enabled());
        store.oidcProviders().put(tenantId, provider);
        return response(provider);
    }

    public OidcConfigRequest configuration(String authorization, long tenantId) {
        requireOwner(authorization, tenantId);
        return response(requiredConfig(tenantId));
    }

    private void requireOwner(String authorization, long tenantId) {
        var claims = permissions.requireContext(authorization, tenantId);
        boolean owner = members.findById(tenantId, claims.memberId())
                .filter(value -> "ACTIVE".equals(value.memberStatus()) && value.owner())
                .isPresent();
        if (!owner) {
            throw new ForbiddenException();
        }
    }

    public OidcStartData start(long tenantId, String redirectUri) {
        OidcProvider config = requiredConfig(tenantId);
        if (!config.enabled()) {
            throw new NotFoundException("OIDC 登录未启用");
        }
        URI redirect = redirectUri(redirectUri);
        String state = token(24);
        String nonce = token(24);
        String verifier = token(32);
        String challenge = sha256Base64(verifier);
        Instant expiresAt = Instant.now().plusSeconds(300);
        store.oidcAuthorizations().put(state, new OidcAuthorization(tenantId, redirectUri, nonce,
                verifier, expiresAt));
        String url = UriComponentsBuilder.fromUriString(config.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", challenge)
                .build().encode().toUriString();
        return new OidcStartData(url, state, expiresAt);
    }

    public LoginData callback(long tenantId, OidcCallbackRequest request) {
        OidcAuthorization authorization = store.oidcAuthorizations().remove(request.state());
        if (authorization == null || authorization.tenantId() != tenantId
                || !authorization.expiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException();
        }
        OidcProvider config = requiredConfig(tenantId);
        try {
            TokenResponse tokens = exchangeCode(config, authorization, request.code());
            Jwt jwt = decode(config, tokens.idToken());
            validateClaims(config, authorization, jwt);
            String email = jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT);
            UserEntity user = users.findByEmail(email)
                    .filter(value -> "ACTIVE".equals(value.userStatus()))
                    .orElseThrow(UnauthorizedException::new);
            return sessions.createSession(user, request.deviceFingerprint());
        } catch (JwtException | RestClientException | IllegalArgumentException exception) {
            throw new UnauthorizedException();
        }
    }

    private TokenResponse exchangeCode(OidcProvider config, OidcAuthorization authorization,
            String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", authorization.redirectUri());
        form.add("client_id", config.clientId());
        form.add("client_secret", properties.oidcSecret(config.clientSecretRef()));
        form.add("code_verifier", authorization.codeVerifier());
        TokenResponse response = restClient.post().uri(config.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(TokenResponse.class);
        if (response == null || response.idToken() == null || response.idToken().isBlank()) {
            throw new UnauthorizedException();
        }
        return response;
    }

    private Jwt decode(OidcProvider config, String idToken) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(config.issuer()));
        return decoder.decode(idToken);
    }

    private void validateClaims(OidcProvider config, OidcAuthorization authorization, Jwt jwt) {
        if (!jwt.getAudience().contains(config.clientId())
                || !MessageDigest.isEqual(bytes(authorization.nonce()),
                        bytes(jwt.getClaimAsString("nonce")))) {
            throw new UnauthorizedException();
        }
        if (jwt.getAudience().size() > 1
                && !config.clientId().equals(jwt.getClaimAsString("azp"))) {
            throw new UnauthorizedException();
        }
        String email = jwt.getClaimAsString("email");
        Boolean verified = jwt.getClaim("email_verified");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(verified)) {
            throw new UnauthorizedException();
        }
        if (config.emailDomain() != null && !config.emailDomain().isBlank()
                && !email.toLowerCase(Locale.ROOT).endsWith("@" + config.emailDomain())) {
            throw new UnauthorizedException();
        }
    }

    private OidcProvider requiredConfig(long tenantId) {
        OidcProvider value = store.oidcProviders().get(tenantId);
        if (value == null) {
            throw new NotFoundException("OIDC 配置不存在");
        }
        return value;
    }

    private OidcConfigRequest response(OidcProvider provider) {
        return new OidcConfigRequest(provider.issuer(), provider.clientId(),
                provider.clientSecretRef(), provider.authorizationEndpoint(),
                provider.tokenEndpoint(), provider.jwksUri(), provider.emailDomain(),
                provider.enabled());
    }

    private URI secureUri(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("OIDC URL 无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new BadRequestException("OIDC URL 必须为不含用户信息的 HTTPS 地址");
        }
        return uri;
    }

    private URI redirectUri(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("OIDC 回调地址无效");
        }
        boolean localhost = "localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
        boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme())
                || (localhost && "http".equalsIgnoreCase(uri.getScheme()));
        if (!allowedScheme || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new BadRequestException("OIDC 回调地址必须使用 HTTPS（本机开发除外）");
        }
        return uri;
    }

    private void sameOrigin(URI issuer, URI endpoint) {
        int issuerPort = issuer.getPort() < 0 ? 443 : issuer.getPort();
        int endpointPort = endpoint.getPort() < 0 ? 443 : endpoint.getPort();
        if (!issuer.getHost().equalsIgnoreCase(endpoint.getHost()) || issuerPort != endpointPort) {
            throw new BadRequestException("OIDC 端点必须与 issuer 同源");
        }
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256Base64(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private String normalizeDomain(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("id_token") String idToken) {
    }
}
