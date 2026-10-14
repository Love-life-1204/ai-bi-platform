package com.aibi.platform.commercial.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateOrderRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelConfigRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelGatewayDecision;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelGatewayRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.PaymentResult;
import com.aibi.platform.commercial.dto.CommercialDtos.ReconcileUsageRequest;
import com.aibi.platform.commercial.model.CommercialModels.ModelConfig;
import com.aibi.platform.commercial.model.CommercialModels.Order;
import com.aibi.platform.commercial.model.CommercialModels.PlanVersion;
import com.aibi.platform.commercial.model.CommercialModels.QuotaBalance;
import com.aibi.platform.commercial.model.CommercialModels.Subscription;
import com.aibi.platform.commercial.model.CommercialModels.UsageSummary;
import com.aibi.platform.commercial.service.CommercialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
public class BillingController {

    private final CommercialService service;

    public BillingController(CommercialService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    public List<PlanVersion> plans() {
        return service.plans();
    }

    @GetMapping("/subscriptions/current")
    public Subscription subscription(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.currentSubscription(authorization, tenantId);
    }

    @PostMapping({"/orders", "/subscriptions/current/change-plan"})
    public Order createOrder(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(authorization, tenantId, idempotencyKey, request);
    }

    @GetMapping("/orders")
    public List<Order> orders(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.orders(authorization, tenantId);
    }

    @GetMapping("/orders/{orderId}")
    public Order order(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long orderId) {
        return service.order(authorization, tenantId, orderId);
    }

    @PostMapping("/payment/callbacks/{provider}")
    public PaymentResult payment(@PathVariable String provider,
            @RequestHeader("X-Payment-Timestamp") String timestamp,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody byte[] rawBody) {
        return service.paymentCallback(provider, timestamp, signature, rawBody);
    }

    @GetMapping("/usage/quotas")
    public List<QuotaBalance> quotas(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.quotas(authorization, tenantId);
    }

    @GetMapping("/usage/ai")
    public UsageSummary usage(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.usage(authorization, tenantId);
    }

    @PostMapping("/usage/ai/reconcile")
    public UsageSummary reconcile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ReconcileUsageRequest request) {
        return service.reconcile(authorization, tenantId, request);
    }

    @PutMapping("/ai/model-config")
    public ModelConfig configureModel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ModelConfigRequest request) {
        return service.configureModel(authorization, tenantId, request);
    }

    @PostMapping("/ai/model-gateway/decision")
    public ModelGatewayDecision modelDecision(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ModelGatewayRequest request) {
        return service.modelDecision(authorization, tenantId, request);
    }
}
