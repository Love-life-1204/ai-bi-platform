package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.dto.CommercialDtos.MfaEnrollment;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcCallbackRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcConfigRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.OidcStartData;
import com.aibi.platform.commercial.dto.CommercialDtos.VerifyMfaRequest;
import com.aibi.platform.identity.service.MfaService;
import com.aibi.platform.identity.service.OidcService;
import com.aibi.platform.identity.dto.LoginData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class EnterpriseAuthController {

    private final MfaService mfa;
    private final OidcService oidc;

    public EnterpriseAuthController(MfaService mfa, OidcService oidc) {
        this.mfa = mfa;
        this.oidc = oidc;
    }

    @PostMapping("/auth/mfa/enroll")
    public MfaEnrollment enroll(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return mfa.enroll(authorization);
    }

    @PostMapping("/auth/mfa/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody VerifyMfaRequest request) {
        mfa.confirm(authorization, request.code());
    }

    @DeleteMapping("/auth/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody VerifyMfaRequest request) {
        mfa.disable(authorization, request.code());
    }

    @PutMapping("/enterprise/oidc")
    public OidcConfigRequest configureOidc(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody OidcConfigRequest request) {
        return oidc.configure(authorization, tenantId, request);
    }

    @GetMapping("/enterprise/oidc")
    public OidcConfigRequest oidcConfiguration(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return oidc.configuration(authorization, tenantId);
    }

    @GetMapping("/auth/oidc/{tenantId}/start")
    public OidcStartData oidcStart(@PathVariable @Positive @JsonResourceId long tenantId,
            @RequestParam String redirectUri) {
        return oidc.start(tenantId, redirectUri);
    }

    @PostMapping("/auth/oidc/{tenantId}/callback")
    public LoginData oidcCallback(@PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody OidcCallbackRequest request) {
        return oidc.callback(tenantId, request);
    }
}
