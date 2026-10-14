package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.CreateRowPolicyRequest;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationRequest;
import com.aibi.platform.identity.dto.GrantPolicyRequest;
import com.aibi.platform.identity.dto.SetColumnPolicyRequest;
import com.aibi.platform.identity.entity.ColumnPolicyEntity;
import com.aibi.platform.identity.entity.PolicyGrantEntity;
import com.aibi.platform.identity.entity.RowPolicyEntity;
import com.aibi.platform.identity.service.DataPolicyService;
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
@Tag(name = "数据权限", description = "行级 RLS 与列级 CLS 数据策略")
public class DataPolicyController {

    private final DataPolicyService dataPolicyService;

    public DataPolicyController(DataPolicyService dataPolicyService) {
        this.dataPolicyService = dataPolicyService;
    }

    @PostMapping("/data-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建行级数据策略")
    public RowPolicyEntity createRowPolicy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateRowPolicyRequest request) {
        return dataPolicyService.createRowPolicy(authorization, tenantId, request);
    }

    @PostMapping("/data-policies/{policyId}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "授予行级数据策略")
    public PolicyGrantEntity grant(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long policyId,
            @Valid @RequestBody GrantPolicyRequest request) {
        return dataPolicyService.grant(authorization, tenantId, policyId, request);
    }

    @PostMapping("/column-policies")
    @Operation(summary = "设置列级数据策略")
    public ColumnPolicyEntity setColumnPolicy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody SetColumnPolicyRequest request) {
        return dataPolicyService.setColumnPolicy(authorization, tenantId, request);
    }

    @PostMapping("/data-policies/evaluate")
    @Operation(summary = "计算数据行列访问范围",
            description = "同组行策略执行 OR、不同组执行 AND；列策略执行 DENY > MASKED > VISIBLE。")
    public DataPolicyEvaluationData evaluate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody DataPolicyEvaluationRequest request) {
        return dataPolicyService.evaluate(authorization, tenantId, request);
    }
}
