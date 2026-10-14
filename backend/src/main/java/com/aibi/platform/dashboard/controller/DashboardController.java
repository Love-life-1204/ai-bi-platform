package com.aibi.platform.dashboard.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateDashboardRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardData;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardQueryData;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardQueryRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.UpdateDashboardRequest;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import com.aibi.platform.dashboard.service.DashboardService;
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
@RequestMapping({"/api/dashboards", "/api/v1/dashboards"})
@Tag(name = "看板", description = "网格布局、筛选联动、下钻及权限查询")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public List<Dashboard> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @PostMapping
    public DashboardData create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateDashboardRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @GetMapping("/{dashboardId}")
    public DashboardData get(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long dashboardId) {
        return service.get(authorization, tenantId, dashboardId);
    }

    @PatchMapping("/{dashboardId}")
    public DashboardData update(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long dashboardId,
            @Valid @RequestBody UpdateDashboardRequest request) {
        return service.update(authorization, tenantId, dashboardId, request);
    }

    @PostMapping("/{dashboardId}/publish")
    public DashboardVersion publish(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long dashboardId) {
        return service.publish(authorization, tenantId, dashboardId);
    }

    @GetMapping("/{dashboardId}/versions")
    public List<DashboardVersion> versions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long dashboardId) {
        return service.versions(authorization, tenantId, dashboardId);
    }

    @PostMapping("/{dashboardId}/query")
    @Operation(summary = "按当前查看者权限加载看板，单图失败不影响其他组件")
    public DashboardQueryData query(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long dashboardId,
            @Valid @RequestBody DashboardQueryRequest request) {
        return service.query(authorization, tenantId, dashboardId, request);
    }
}
