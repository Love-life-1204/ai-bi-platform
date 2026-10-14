package com.aibi.platform.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.dto.ChangeTenantStatusRequest;
import com.aibi.platform.common.dto.TenantData;
import com.aibi.platform.common.dto.UpdateTenantRequest;
import com.aibi.platform.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenants", "/api/v1/tenants"})
@Tag(name = "租户", description = "SaaS 企业租户管理接口")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建租户", description = "创建全局唯一 tenant_code 的企业租户。")
    public TenantData createTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTenantRequest request) {
        return tenantService.createTenant(authorization, request, idempotencyKey);
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "查询租户基本信息", description = "按租户 ID 查询未删除租户的基本信息。")
    public TenantData getTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId) {
        return tenantService.getTenant(authorization, tenantId);
    }

    @PutMapping("/{tenantId}")
    @Operation(summary = "修改租户基本信息", description = "tenant_code 和租户状态不允许通过此接口修改。")
    public TenantData updateTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.updateTenant(authorization, tenantId, request);
    }

    @PostMapping("/{tenantId}/freeze")
    @Operation(summary = "冻结租户", description = "冻结后租户成员不能建立新的权限上下文。")
    public TenantData freezeTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ChangeTenantStatusRequest request) {
        return tenantService.freezeTenant(authorization, tenantId, request);
    }

    @PostMapping("/{tenantId}/unfreeze")
    @Operation(summary = "解冻租户", description = "将已冻结租户恢复为活动状态。")
    public TenantData unfreezeTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ChangeTenantStatusRequest request) {
        return tenantService.unfreezeTenant(authorization, tenantId, request);
    }
}
