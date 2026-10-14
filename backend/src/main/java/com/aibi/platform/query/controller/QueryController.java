package com.aibi.platform.query.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.query.dto.QueryDtos.CancelData;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.QueryStatusData;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.aibi.platform.query.service.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/queries", "/api/v1/queries"})
@Tag(name = "统一数据查询", description = "BI 与 AI 共用的版本化、权限受控查询服务")
public class QueryController {

    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    @PostMapping({"", "/plans/execute"})
    @Operation(summary = "执行强类型受控查询计划")
    public QueryResult execute(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody QueryPlan plan) {
        return service.execute(authorization, tenantId, plan);
    }

    @PostMapping("/validate")
    @Operation(summary = "校验查询计划、版本、权限和编译安全")
    public ValidationData validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody QueryPlan plan) {
        return service.validate(authorization, tenantId, plan);
    }

    @PostMapping("/async")
    @Operation(summary = "提交可查询状态和取消的异步受控查询")
    public QueryStatusData submit(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody QueryPlan plan) {
        return service.submit(authorization, tenantId, plan);
    }

    @GetMapping("/{queryId}")
    @Operation(summary = "查询任务状态和版本追溯信息")
    public QueryStatusData status(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @NotBlank String queryId) {
        return service.status(authorization, tenantId, queryId);
    }

    @GetMapping("/{queryId}/result")
    @Operation(summary = "读取权限隔离的查询结果")
    public QueryResult result(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @NotBlank String queryId) {
        return service.result(authorization, tenantId, queryId);
    }

    @PostMapping("/{queryId}/cancel")
    @Operation(summary = "取消数据库实际查询")
    public CancelData cancel(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @NotBlank String queryId) {
        return service.cancel(authorization, tenantId, queryId);
    }
}
