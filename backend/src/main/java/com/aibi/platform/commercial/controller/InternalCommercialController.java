package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.dto.CommercialDtos.EvaluateAlertRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.QuotaChangeRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.QuotaConfirmRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UsageRequest;
import com.aibi.platform.commercial.model.CommercialModels.AlertEvent;
import com.aibi.platform.commercial.model.CommercialModels.QuotaReservation;
import com.aibi.platform.commercial.model.CommercialModels.UsageEntry;
import com.aibi.platform.commercial.service.CommercialAccessService;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequestMapping("/internal/v1/commercial/tenants/{tenantId}")
public class InternalCommercialController {

    private final CommercialService service;
    private final CommercialAccessService access;

    public InternalCommercialController(CommercialService service, CommercialAccessService access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/quotas/reserve")
    public QuotaReservation reserve(@RequestHeader("X-Internal-Token") String token,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody QuotaChangeRequest request) {
        access.requireInternal(token);
        return service.reserve(tenantId, request.quotaCode(), request.reservationKey(),
                request.amount());
    }

    @PostMapping("/quotas/{reservationKey}/confirm")
    public QuotaReservation confirm(@RequestHeader("X-Internal-Token") String token,
            @PathVariable @Positive @JsonResourceId long tenantId, @PathVariable String reservationKey,
            @Valid @RequestBody QuotaConfirmRequest request) {
        access.requireInternal(token);
        return service.confirm(tenantId, reservationKey, request);
    }

    @PostMapping("/quotas/{reservationKey}/release")
    public QuotaReservation release(@RequestHeader("X-Internal-Token") String token,
            @PathVariable @Positive @JsonResourceId long tenantId, @PathVariable String reservationKey,
            @RequestParam String eventKey) {
        access.requireInternal(token);
        return service.release(tenantId, reservationKey, eventKey);
    }

    @PostMapping("/usage")
    public UsageEntry usage(@RequestHeader("X-Internal-Token") String token,
            @PathVariable @Positive @JsonResourceId long tenantId, @Valid @RequestBody UsageRequest request) {
        access.requireInternal(token);
        return service.recordUsage(tenantId, request);
    }

    @PostMapping("/alerts/{ruleId}/evaluate")
    public AlertEvent evaluate(@RequestHeader("X-Internal-Token") String token,
            @PathVariable @Positive @JsonResourceId long tenantId, @PathVariable @Positive @JsonResourceId long ruleId,
            @Valid @RequestBody EvaluateAlertRequest request) {
        access.requireInternal(token);
        return service.evaluateAlert(tenantId, ruleId, request);
    }
}
