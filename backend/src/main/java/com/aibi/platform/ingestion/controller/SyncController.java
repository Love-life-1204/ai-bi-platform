package com.aibi.platform.ingestion.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.ingestion.dto.IngestionDtos.CompensationRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateSyncJobRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncJobData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.TaskAccepted;
import com.aibi.platform.ingestion.dto.IngestionDtos.UpdateSyncJobRequest;
import com.aibi.platform.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
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
@RequestMapping({"/api", "/api/v1"})
@Tag(name = "数据同步", description = "全量/增量同步、租约、批次和人工补偿")
public class SyncController {

    private final IngestionService service;

    public SyncController(IngestionService service) {
        this.service = service;
    }

    @PostMapping("/sync-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增同步任务")
    public SyncJobData create(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateSyncJobRequest request) {
        return service.createSyncJob(authorization, tenantId, request);
    }

    @GetMapping("/sync-jobs")
    @Operation(summary = "同步任务列表")
    public List<SyncJobData> jobs(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.listSyncJobs(authorization, tenantId);
    }

    @GetMapping("/sync-jobs/{jobId}")
    @Operation(summary = "同步任务配置与水位")
    public SyncJobData job(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long jobId) {
        return service.syncJob(authorization, tenantId, jobId);
    }

    @PatchMapping("/sync-jobs/{jobId}")
    @Operation(summary = "编辑同步任务")
    public SyncJobData update(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long jobId,
            @Valid @RequestBody UpdateSyncJobRequest request) {
        return service.updateSyncJob(authorization, tenantId, jobId, request);
    }

    @PostMapping("/sync-jobs/{jobId}/enable")
    @Operation(summary = "启用同步任务")
    public SyncJobData enable(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long jobId,
            @RequestParam @PositiveOrZero long lockVersion) {
        return service.setSyncJobEnabled(authorization, tenantId, jobId, true, lockVersion);
    }

    @PostMapping("/sync-jobs/{jobId}/pause")
    @Operation(summary = "暂停同步任务")
    public SyncJobData pause(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long jobId,
            @RequestParam @PositiveOrZero long lockVersion) {
        return service.setSyncJobEnabled(authorization, tenantId, jobId, false, lockVersion);
    }

    @PostMapping("/sync-jobs/{jobId}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "幂等触发同步运行")
    public TaskAccepted run(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable @Positive @JsonResourceId long jobId) {
        return service.runSync(authorization, tenantId, jobId, idempotencyKey);
    }

    @GetMapping("/sync-runs")
    @Operation(summary = "同步运行与批次进度")
    public List<SyncRunData> runs(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long jobId) {
        return service.listRuns(authorization, tenantId, jobId);
    }

    @GetMapping("/sync-runs/{runId}")
    @Operation(summary = "同步批次校验、对账和失败详情")
    public SyncRunData run(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long runId) {
        return service.syncRun(authorization, tenantId, runId);
    }

    @PostMapping("/sync-runs/{runId}/cancel")
    @Operation(summary = "取消同步运行")
    public SyncRunData cancel(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long runId) {
        return service.cancelRun(authorization, tenantId, runId);
    }

    @PostMapping("/sync-runs/{runId}/compensate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "对失败或死信运行发起人工补偿")
    public TaskAccepted compensate(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long runId,
            @Valid @RequestBody CompensationRequest request) {
        return service.compensate(authorization, tenantId, runId, request);
    }
}
