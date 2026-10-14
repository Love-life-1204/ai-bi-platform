package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateExportRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateReportRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateReportSubscriptionRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ExportDownload;
import com.aibi.platform.commercial.dto.CommercialDtos.UpdateReportSubscriptionRequest;
import com.aibi.platform.commercial.model.CommercialModels.ExportTask;
import com.aibi.platform.commercial.model.CommercialModels.Report;
import com.aibi.platform.commercial.model.CommercialModels.ReportSubscription;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class ReportController {

    private final CommercialService service;

    public ReportController(CommercialService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    public List<Report> reports(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.reports(authorization, tenantId);
    }

    @PostMapping("/reports")
    public Report createReport(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateReportRequest request) {
        return service.createReport(authorization, tenantId, request);
    }

    @GetMapping("/reports/{reportId}")
    public Report report(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long reportId) {
        return service.report(authorization, tenantId, reportId);
    }

    @PostMapping("/exports")
    public ExportTask createExport(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateExportRequest request) {
        return service.createExport(authorization, tenantId, idempotencyKey, request);
    }

    @GetMapping("/exports/{exportId}")
    public ExportTask export(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long exportId) {
        return service.export(authorization, tenantId, exportId);
    }

    @PostMapping("/exports/{exportId}/cancel")
    public ExportTask cancel(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long exportId) {
        return service.cancelExport(authorization, tenantId, exportId);
    }

    @GetMapping("/exports/{exportId}/download")
    public ResponseEntity<byte[]> download(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long exportId) {
        ExportDownload value = service.download(authorization, tenantId, exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(value.fileName()).build().toString())
                .body(value.content());
    }

    @GetMapping("/report-subscriptions")
    public List<ReportSubscription> subscriptions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.reportSubscriptions(authorization, tenantId);
    }

    @PostMapping("/report-subscriptions")
    public ReportSubscription createSubscription(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateReportSubscriptionRequest request) {
        return service.createReportSubscription(authorization, tenantId, request);
    }

    @PatchMapping("/report-subscriptions/{subscriptionId}")
    public ReportSubscription updateSubscription(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long subscriptionId,
            @Valid @RequestBody UpdateReportSubscriptionRequest request) {
        return service.updateReportSubscription(authorization, tenantId, subscriptionId, request);
    }
}
