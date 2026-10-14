package com.aibi.platform.source.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.source.dto.ConnectionTestData;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.dto.DatasourceData;
import com.aibi.platform.source.dto.DatasourceDependencyData;
import com.aibi.platform.source.dto.MetadataDiffData;
import com.aibi.platform.source.dto.TestConnectionRequest;
import com.aibi.platform.source.dto.UpdateDatasourceRequest;
import com.aibi.platform.source.model.MetadataSnapshot;
import com.aibi.platform.source.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpHeaders;

@Validated
@RestController
@RequestMapping({"/api/datasources", "/api/v1/datasources"})
@Tag(name = "数据源", description = "只读数据库连接与元数据管理")
public class DatasourceController {

    private final DatasourceService service;

    public DatasourceController(DatasourceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增数据源")
    public DatasourceData create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateDatasourceRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @PutMapping("/{datasourceId}")
    @Operation(summary = "编辑数据源")
    public DatasourceData update(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId, @Valid @RequestBody UpdateDatasourceRequest request) {
        return service.update(authorization, tenantId, datasourceId, request);
    }

    @GetMapping
    @Operation(summary = "数据源列表")
    public List<DatasourceData> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @PostMapping("/{datasourceId}/deactivate")
    @Operation(summary = "停用数据源")
    public DatasourceData deactivate(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId, @RequestParam @PositiveOrZero long lockVersion) {
        return service.deactivate(authorization, tenantId, datasourceId, lockVersion);
    }

    @GetMapping("/{datasourceId}/dependencies")
    @Operation(summary = "查询删除依赖")
    public DatasourceDependencyData dependencies(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId) {
        return service.dependencies(authorization, tenantId, datasourceId);
    }

    @DeleteMapping("/{datasourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除已停用且无依赖的数据源")
    public void delete(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId,
            @RequestParam @PositiveOrZero long lockVersion) {
        service.delete(authorization, tenantId, datasourceId, lockVersion);
    }

    @PostMapping("/connection-tests")
    @Operation(summary = "保存前测试连接")
    public ConnectionTestData test(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody TestConnectionRequest request) {
        return service.test(authorization, tenantId, request);
    }

    @PostMapping("/{datasourceId}/connection-test")
    @Operation(summary = "测试已保存数据源连接")
    public ConnectionTestData testSaved(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId) {
        return service.test(authorization, tenantId, datasourceId);
    }

    @PostMapping("/{datasourceId}/metadata/collect")
    @Operation(summary = "采集 Schema、表和字段元数据")
    public MetadataSnapshot collect(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId) {
        return service.collectMetadata(authorization, tenantId, datasourceId);
    }

    @GetMapping("/{datasourceId}/metadata/snapshots")
    @Operation(summary = "查询元数据版本")
    public List<MetadataSnapshot> snapshots(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId) {
        return service.snapshots(authorization, tenantId, datasourceId);
    }

    @GetMapping("/{datasourceId}/metadata/diff")
    @Operation(summary = "对比元数据版本")
    public MetadataDiffData diff(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long datasourceId,
            @RequestParam @Positive long fromVersion, @RequestParam @Positive long toVersion) {
        return service.diff(authorization, tenantId, datasourceId, fromVersion, toVersion);
    }
}
