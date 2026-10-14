package com.aibi.platform.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateAlertRuleRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateOrderRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreatePlanRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.EvaluateAlertRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelConfigRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.PaymentResult;
import com.aibi.platform.commercial.dto.CommercialDtos.QuotaConfirmRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UsageRequest;
import com.aibi.platform.commercial.model.CommercialModels.Order;
import com.aibi.platform.commercial.model.CommercialModels.PlanVersion;
import com.aibi.platform.commercial.model.CommercialModels.QuotaBalance;
import com.aibi.platform.commercial.service.CommercialService;
import com.aibi.platform.commercial.service.NotificationDeliveryService;
import com.aibi.platform.commercial.store.CommercialStore;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.security.TokenService.TenantClaims;
import com.aibi.platform.identity.service.PermissionContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommercialServiceTest {

    private CommercialService service;
    private CommercialStore store;
    private CommercialProperties properties;
    private final TenantClaims claims = new TenantClaims(10, 11, 1, 12, 1, 1,
            Instant.now().plusSeconds(900));

    @BeforeEach
    void setUp() {
        store = new CommercialStore();
        properties = new CommercialProperties();
        PermissionContextService permissions = mock(PermissionContextService.class);
        when(permissions.requireContext("Bearer token", 1)).thenReturn(claims);
        MemberMapper members = mock(MemberMapper.class);
        when(members.findById(org.mockito.ArgumentMatchers.eq(1L), anyLong()))
                .thenAnswer(invocation -> Optional.of(member(invocation.getArgument(1))));
        service = new CommercialService(store, properties, permissions, members,
                new ObjectMapper(), mock(NotificationDeliveryService.class));
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void shouldNeverOverReserveQuotaUnderConcurrency() throws Exception {
        Instant now = Instant.now();
        service.initializeQuota(1, "AI_REQUEST", 40, now.minusSeconds(1), now.plusSeconds(300));
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(16)) {
            for (int index = 0; index < 100; index++) {
                int request = index;
                executor.submit(() -> {
                    try {
                        start.await();
                        service.reserve(1, "AI_REQUEST", "reservation-" + request, 1);
                        accepted.incrementAndGet();
                    } catch (ApiException ignored) {
                        assertThat(ignored.getErrorCode()).isEqualTo(ApiErrorCode.QUOTA_EXCEEDED);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        QuotaBalance balance = store.quotas().get("1:AI_REQUEST");
        assertThat(accepted).hasValue(40);
        assertThat(balance.reserved()).isEqualTo(40);
        assertThat(balance.remaining()).isZero();
    }

    @Test
    void shouldConfirmActualUsageAndReleaseUnusedReservationIdempotently() {
        Instant now = Instant.now();
        service.initializeQuota(1, "TOKEN", 100, now.minusSeconds(1), now.plusSeconds(300));
        service.reserve(1, "TOKEN", "reserve-123456", 30);
        var first = service.confirm(1, "reserve-123456", new QuotaConfirmRequest("task-1", 18));
        var replay = service.confirm(1, "reserve-123456", new QuotaConfirmRequest("task-1", 18));
        assertThat(replay).isEqualTo(first);
        assertThat(store.quotas().get("1:TOKEN").used()).isEqualTo(18);
        assertThat(store.quotas().get("1:TOKEN").reserved()).isZero();
    }

    @Test
    void shouldVerifyAndDeduplicatePaymentCallbacks() throws Exception {
        PlanVersion plan = service.createPlanVersion(new CreatePlanRequest("team", "Team", 9900,
                "CNY", "MONTH", Map.of("AI_REQUEST", 100L), Instant.now().minusSeconds(1)));
        Order order = service.createOrder("Bearer token", 1, "order-key-0001",
                new CreateOrderRequest(plan.id()));
        String body = "{\"eventId\":\"evt-1\",\"eventType\":\"PAYMENT_SUCCEEDED\","
                + "\"tenantId\":1,\"orderId\":" + order.id()
                + ",\"amountMinorUnits\":9900,\"currency\":\"CNY\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signature(timestamp, body.getBytes(StandardCharsets.UTF_8));
        PaymentResult first = service.paymentCallback("mockpay", timestamp, signature,
                body.getBytes(StandardCharsets.UTF_8));
        PaymentResult replay = service.paymentCallback("mockpay", timestamp, signature,
                body.getBytes(StandardCharsets.UTF_8));
        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(store.orders().get(order.id()).status()).isEqualTo("PAID");
        assertThat(store.quotas().get("1:AI_REQUEST").limit()).isEqualTo(100);
    }

    @Test
    void shouldRejectDetailDataUnlessExplicitlyApproved() {
        service.configureModel("Bearer token", 1, new ModelConfigRequest("provider", "model",
                "cn", "AGGREGATE_ONLY", true, Map.of("promptPerMillion", 100L,
                        "completionPerMillion", 200L)));
        var denied = service.modelDecision("Bearer token", 1,
                new com.aibi.platform.commercial.dto.CommercialDtos.ModelGatewayRequest(
                        "DETAIL", new ObjectMapper().createObjectNode()));
        assertThat(denied.allowed()).isFalse();
        var first = service.recordUsage(1, new UsageRequest(1, "provider", "request-1", "model",
                1_000_000, 500_000, "CNY"));
        var replay = service.recordUsage(1, new UsageRequest(1, "provider", "request-1", "model",
                1_000_000, 500_000, "CNY"));
        assertThat(replay).isEqualTo(first);
        assertThat(first.amountMinorUnits()).isEqualTo(200);
    }

    @Test
    void shouldSuppressRepeatedAlertButKeepEvidence() {
        var rule = service.createAlertRule("Bearer token", 1,
                new CreateAlertRuleRequest(1, "Revenue drop", "LT", 80,
                        List.of(12L), 600L));
        var json = new ObjectMapper().createObjectNode().put("metric", "revenue");
        var first = service.evaluateAlert(1, rule.id(),
                new EvaluateAlertRequest(50, json, "2026-10-12T10:00"));
        var second = service.evaluateAlert(1, rule.id(),
                new EvaluateAlertRequest(40, json, "2026-10-12T10:05"));
        assertThat(first.status()).isEqualTo("OPEN");
        assertThat(second.status()).isEqualTo("SUPPRESSED");
        assertThat(store.notifications()).hasSize(1);
    }

    @Test
    void shouldRejectInvalidPaymentSignature() {
        assertThatThrownBy(() -> service.paymentCallback("mockpay",
                String.valueOf(Instant.now().getEpochSecond()), "bad", "{}".getBytes()))
                .isInstanceOf(com.aibi.platform.common.exception.UnauthorizedException.class);
    }

    @Test
    void shouldRejectSensitiveCommercialChangesForNonOwner() {
        PermissionContextService permissions = mock(PermissionContextService.class);
        when(permissions.requireContext("Bearer token", 1)).thenReturn(claims);
        MemberMapper members = mock(MemberMapper.class);
        when(members.findById(1L, claims.memberId())).thenReturn(Optional.of(
                new MemberEntity(claims.memberId(), 1, claims.userId(), "member", "ACTIVE",
                        false, 1, Instant.now(), Instant.now(), Instant.now(), 0)));
        CommercialService restricted = new CommercialService(new CommercialStore(), properties,
                permissions, members, new ObjectMapper(), mock(NotificationDeliveryService.class));
        try {
            PlanVersion plan = restricted.createPlanVersion(new CreatePlanRequest("owner-only",
                    "Owner only", 9900, "CNY", "MONTH", Map.of("AI_REQUEST", 100L),
                    Instant.now().minusSeconds(1)));
            assertThatThrownBy(() -> restricted.createOrder("Bearer token", 1,
                    "owner-only-order", new CreateOrderRequest(plan.id())))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> restricted.configureModel("Bearer token", 1,
                    new ModelConfigRequest("provider", "model", "cn", "AGGREGATE_ONLY",
                            true, Map.of("promptPerMillion", 100L))))
                    .isInstanceOf(ForbiddenException.class);
        } finally {
            restricted.close();
        }
    }

    private String signature(String timestamp, byte[] body) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getPaymentWebhookSecret()
                .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private MemberEntity member(long id) {
        Instant now = Instant.now();
        return new MemberEntity(id, 1, id + 100, "member", "ACTIVE", true, 1,
                now, now, now, 0);
    }
}
