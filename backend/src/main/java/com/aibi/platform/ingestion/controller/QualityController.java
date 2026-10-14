package com.aibi.platform.ingestion.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRunData;
import com.aibi.platform.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
@Tag(name = "数据质量", description = "必填、唯一、类型、范围和批次对账")
public class QualityController {

    private final IngestionService service;

    public QualityController(IngestionService service) {
        this.service = service;
    }

    @PostMapping("/quality-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增质量规则")
    public QualityRuleData create(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody QualityRuleRequest request) {
        return service.createQualityRule(authorization, tenantId, request);
    }

    @GetMapping("/quality-rules")
    @Operation(summary = "查询质量规则")
    public List<QualityRuleData> rules(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long datasetId) {
        return service.listQualityRules(authorization, tenantId, datasetId);
    }

    @PostMapping("/quality-runs/{batchId}")
    @Operation(summary = "对指定批次执行质量校验")
    public QualityRunData run(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long batchId) {
        return service.runQuality(authorization, tenantId, batchId);
    }
}
