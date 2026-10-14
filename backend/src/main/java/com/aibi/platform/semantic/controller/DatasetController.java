package com.aibi.platform.semantic.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.DatasetData;
import com.aibi.platform.semantic.dto.SemanticDtos.PreviewData;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.service.DatasetService;
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
@RequestMapping({"/api/datasets", "/api/v1/datasets"})
@Tag(name = "数据集", description = "业务数据集、字段映射、关联、校验、发布与权限预览")
public class DatasetController {

    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建数据集草稿")
    public DatasetData create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateDatasetRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @GetMapping
    @Operation(summary = "查询有权访问的数据集")
    public List<Dataset> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @GetMapping("/{datasetId}")
    @Operation(summary = "查询数据集详情")
    public DatasetData get(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId) {
        return service.get(authorization, tenantId, datasetId);
    }

    @PatchMapping("/{datasetId}")
    @Operation(summary = "修改数据集草稿或基于发布版创建新草稿")
    public DatasetData update(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId,
            @Valid @RequestBody UpdateDatasetRequest request) {
        return service.update(authorization, tenantId, datasetId, request);
    }

    @PostMapping("/{datasetId}/validate")
    @Operation(summary = "校验字段映射、关联审批和基数")
    public ValidationResult validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId) {
        return service.validate(authorization, tenantId, datasetId);
    }

    @PostMapping("/{datasetId}/publish")
    @Operation(summary = "发布不可变数据集版本")
    public DatasetVersion publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId) {
        return service.publish(authorization, tenantId, datasetId);
    }

    @GetMapping("/{datasetId}/versions")
    @Operation(summary = "查询数据集历史版本")
    public List<DatasetVersion> versions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId) {
        return service.versions(authorization, tenantId, datasetId);
    }

    @GetMapping("/{datasetId}/preview")
    @Operation(summary = "按当前成员行列级权限预览数据集")
    public PreviewData preview(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasetId) {
        return service.preview(authorization, tenantId, datasetId);
    }
}
