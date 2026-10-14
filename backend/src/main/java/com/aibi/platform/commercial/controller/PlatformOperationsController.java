package com.aibi.platform.commercial.controller;

import com.aibi.platform.commercial.dto.CommercialDtos.CreatePlanRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.PlatformOverview;
import com.aibi.platform.commercial.dto.CommercialDtos.SupportGrantRequest;
import com.aibi.platform.commercial.model.CommercialModels.PlanVersion;
import com.aibi.platform.commercial.model.CommercialModels.SupportGrant;
import com.aibi.platform.commercial.service.CommercialAccessService;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/platform", "/api/v1/platform"})
public class PlatformOperationsController {

    private final CommercialService service;
    private final CommercialAccessService access;

    public PlatformOperationsController(CommercialService service, CommercialAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/overview")
    public PlatformOverview overview(@RequestHeader("X-Platform-Token") String token) {
        access.requirePlatform(token);
        return service.overview();
    }

    @GetMapping("/tenants")
    public PlatformOverview tenants(@RequestHeader("X-Platform-Token") String token) {
        access.requirePlatform(token);
        return service.overview();
    }

    @PostMapping("/plans")
    public PlanVersion createPlan(@RequestHeader("X-Platform-Token") String token,
            @Valid @RequestBody CreatePlanRequest request) {
        access.requirePlatform(token);
        return service.createPlanVersion(request);
    }

    @PostMapping("/support-grants")
    public SupportGrant createGrant(@RequestHeader("X-Platform-Token") String token,
            @Valid @RequestBody SupportGrantRequest request) {
        access.requirePlatform(token);
        return service.createSupportGrant(request);
    }

    @GetMapping("/support-grants")
    public List<SupportGrant> grants(@RequestHeader("X-Platform-Token") String token) {
        access.requirePlatform(token);
        return service.supportGrants();
    }
}
