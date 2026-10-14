package com.aibi.platform.semantic.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.MetricData;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.aibi.platform.semantic.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/metrics", "/api/v1/metrics"})
@Tag(name = "指标", description = "指标公式、时间币种规则、依赖关系和版本发布")
public class MetricController {

    private final MetricService service;

    public MetricController(MetricService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建指标草稿")
    public MetricData create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateMetricRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @GetMapping
    @Operation(summary = "查询指标")
    public List<Metric> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @GetMapping("/{metricId}")
    @Operation(summary = "查询指标详情")
    public MetricData get(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long metricId) {
        return service.get(authorization, tenantId, metricId);
    }

    @PatchMapping("/{metricId}")
    @Operation(summary = "修改指标草稿或基于发布版创建新草稿")
    public MetricData update(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long metricId,
            @Valid @RequestBody UpdateMetricRequest request) {
        return service.update(authorization, tenantId, metricId, request);
    }

    @PostMapping("/{metricId}/validate")
    @Operation(summary = "校验公式、时间、币种与循环依赖")
    public ValidationResult validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long metricId) {
        return service.validate(authorization, tenantId, metricId);
    }

    @PostMapping("/{metricId}/publish")
    @Operation(summary = "发布不可变指标版本")
    public MetricVersion publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long metricId) {
        return service.publish(authorization, tenantId, metricId);
    }

    @GetMapping("/{metricId}/versions")
    @Operation(summary = "查询指标历史版本")
    public List<MetricVersion> versions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long metricId) {
        return service.versions(authorization, tenantId, metricId);
    }
}
