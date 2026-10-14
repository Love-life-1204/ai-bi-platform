package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateAlertRuleRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UpdateAlertRuleRequest;
import com.aibi.platform.commercial.model.CommercialModels.AlertEvent;
import com.aibi.platform.commercial.model.CommercialModels.AlertRule;
import com.aibi.platform.commercial.model.CommercialModels.Notification;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
public class AlertController {

    private final CommercialService service;

    public AlertController(CommercialService service) {
        this.service = service;
    }

    @GetMapping("/alert-rules")
    public List<AlertRule> rules(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.alertRules(authorization, tenantId);
    }

    @PostMapping("/alert-rules")
    public AlertRule createRule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateAlertRuleRequest request) {
        return service.createAlertRule(authorization, tenantId, request);
    }

    @PatchMapping("/alert-rules/{ruleId}")
    public AlertRule updateRule(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long ruleId,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return service.updateAlertRule(authorization, tenantId, ruleId, request);
    }

    @GetMapping("/alert-events")
    public List<AlertEvent> events(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.alertEvents(authorization, tenantId);
    }

    @PostMapping("/alert-events/{eventId}/ack")
    public AlertEvent acknowledge(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long eventId) {
        return service.acknowledgeAlert(authorization, tenantId, eventId);
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.notifications(authorization, tenantId);
    }

    @PostMapping("/notifications/{notificationId}/read")
    public Notification read(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long notificationId) {
        return service.readNotification(authorization, tenantId, notificationId);
    }
}
