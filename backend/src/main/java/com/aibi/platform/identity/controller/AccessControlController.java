package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.AccessDecisionData;
import com.aibi.platform.identity.dto.AssetData;
import com.aibi.platform.identity.dto.CreateAssetRequest;
import com.aibi.platform.identity.dto.GrantAssetAclRequest;
import com.aibi.platform.identity.service.AccessDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenants/{tenantId}", "/api/v1/tenants/{tenantId}"})
@Tag(name = "访问控制", description = "RBAC 操作权限与资产 ACL")
public class AccessControlController {

    private final AccessDecisionService accessDecisionService;

    public AccessControlController(AccessDecisionService accessDecisionService) {
        this.accessDecisionService = accessDecisionService;
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "登记授权资产")
    public AssetData createAsset(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateAssetRequest request) {
        return accessDecisionService.createAsset(authorization, tenantId, request);
    }

    @PostMapping("/assets/{assetId}/acl")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "设置资产 ACL")
    public void setAcl(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long assetId,
            @Valid @RequestBody GrantAssetAclRequest request) {
        accessDecisionService.setAcl(authorization, tenantId, assetId, request);
    }

    @PostMapping("/access/check")
    @Operation(summary = "评估访问权限")
    public AccessDecisionData check(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody AccessCheckRequest request) {
        return accessDecisionService.check(authorization, tenantId, request);
    }

    @PostMapping("/access/require")
    @Operation(summary = "强制访问权限", description = "无权时返回 HTTP 403。")
    public AccessDecisionData require(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody AccessCheckRequest request) {
        return accessDecisionService.require(authorization, tenantId, request);
    }
}
