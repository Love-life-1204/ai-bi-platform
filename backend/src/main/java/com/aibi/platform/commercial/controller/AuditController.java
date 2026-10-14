package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.model.CommercialModels.AuditEvent;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/audit-events", "/api/v1/audit-events"})
public class AuditController {

    private final CommercialService service;

    public AuditController(CommercialService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditEvent> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.audits(authorization, tenantId);
    }
}
