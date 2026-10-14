package com.aibi.platform.dashboard.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.dashboard.dto.DashboardDtos.ChartData;
import com.aibi.platform.dashboard.dto.DashboardDtos.ChartValidationData;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateChartRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.UpdateChartRequest;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.service.ChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/charts", "/api/v1/charts"})
@Tag(name = "图表", description = "安全图表规范与不可变发布版本")
public class ChartController {

    private final ChartService service;

    public ChartController(ChartService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页前的有权图表列表")
    public List<Chart> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @PostMapping
    @Operation(summary = "创建图表及首个草稿版本")
    public ChartData create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateChartRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @GetMapping("/{chartId}")
    @Operation(summary = "读取图表及可见草稿")
    public ChartData get(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long chartId) {
        return service.get(authorization, tenantId, chartId);
    }

    @PatchMapping("/{chartId}")
    @Operation(summary = "更新图表草稿，已发布图表自动创建后继草稿")
    public ChartData update(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long chartId,
            @Valid @RequestBody UpdateChartRequest request) {
        return service.update(authorization, tenantId, chartId, request);
    }

    @PostMapping("/{chartId}/validate")
    @Operation(summary = "校验数据类型、查询计划与安全图表配置")
    public ChartValidationData validate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long chartId) {
        return service.validate(authorization, tenantId, chartId);
    }

    @PostMapping("/{chartId}/publish")
    @Operation(summary = "发布不可变图表版本")
    public ChartVersion publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long chartId) {
        return service.publish(authorization, tenantId, chartId);
    }

    @GetMapping("/{chartId}/versions")
    @Operation(summary = "列出图表历史版本")
    public List<ChartVersion> versions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long chartId) {
        return service.versions(authorization, tenantId, chartId);
    }
}
