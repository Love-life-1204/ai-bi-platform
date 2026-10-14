package com.aibi.platform.commercial.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CommercialDtos {

    private CommercialDtos() {
    }

    public record CreatePlanRequest(@NotBlank String planCode, @NotBlank String planName,
            @PositiveOrZero long priceMinorUnits, @NotBlank String currency,
            @NotBlank String billingPeriod, @NotEmpty Map<String, @PositiveOrZero Long> quotas,
            Instant effectiveFrom) {
    }

    public record CreateOrderRequest(@Positive @JsonResourceId long planVersionId) {
    }

    public record PaymentCallbackRequest(@NotBlank String eventId, @NotBlank String eventType,
            @Positive @JsonResourceId long tenantId, @Positive @JsonResourceId long orderId, @PositiveOrZero long amountMinorUnits,
            @NotBlank String currency, String refundId) {
    }

    public record PaymentResult(String eventId, String status, boolean replayed) {
    }

    public record QuotaChangeRequest(@NotBlank String quotaCode, @NotBlank String reservationKey,
            @Positive long amount) {
    }

    public record QuotaConfirmRequest(@NotBlank String businessEventKey,
            @PositiveOrZero long actualAmount) {
    }

    public record ModelConfigRequest(@NotBlank String providerCode, @NotBlank String modelName,
            @NotBlank String region, @NotBlank String dataPolicy, boolean externalEnabled,
            @NotEmpty Map<String, @PositiveOrZero Long> prices) {
    }

    public record ModelGatewayRequest(@NotBlank String dataClassification,
            @NotNull JsonNode payload) {
    }

    public record ModelGatewayDecision(boolean allowed, String providerCode, String modelName,
            String dataPolicy, String reason) {
    }

    public record UsageRequest(@Positive @JsonResourceId long taskId, @NotBlank String providerCode,
            @NotBlank String providerRequestId, @NotBlank String modelName,
            @PositiveOrZero long promptTokens, @PositiveOrZero long completionTokens,
            String currency) {
    }

    public record ReconcileUsageRequest(@NotBlank String providerCode,
            @PositiveOrZero long providerAmountMinorUnits, @NotBlank String currency,
            Instant from, Instant to) {
    }

    public record CreateReportRequest(@Positive @JsonResourceId long workspaceId, @NotBlank String name,
            @NotEmpty List<@NotBlank String> columns, @NotNull List<List<String>> rows) {
    }

    public record CreateExportRequest(@Positive @JsonResourceId long reportId, @NotBlank String format) {
    }

    public record ExportDownload(String fileName, String contentType, byte[] content) {
    }

    public record CreateReportSubscriptionRequest(@Positive @JsonResourceId long reportId,
            @NotBlank String schedule, @NotBlank String timezone,
            @NotEmpty @JsonResourceIds List<@Positive Long> recipientMemberIds, Instant nextRunAt) {
    }

    public record UpdateReportSubscriptionRequest(String schedule, String timezone,
            @JsonResourceIds List<@Positive Long> recipientMemberIds, String status, Instant nextRunAt) {
    }

    public record CreateAlertRuleRequest(@Positive @JsonResourceId long workspaceId, @NotBlank String name,
            @NotBlank String operator, double threshold, @NotEmpty @JsonResourceIds List<@Positive Long> recipients,
            @PositiveOrZero Long suppressionSeconds) {
    }

    public record UpdateAlertRuleRequest(String name, String operator, Double threshold,
            @JsonResourceIds List<@Positive Long> recipients, String status, @PositiveOrZero Long suppressionSeconds) {
    }

    public record EvaluateAlertRequest(double value, @NotNull JsonNode evidence,
            @NotBlank String evaluationWindow) {
    }

    public record SupportGrantRequest(@Positive @JsonResourceId long tenantId, @Positive @JsonResourceId long approverMemberId,
            @Positive @JsonResourceId long supportUserId, @NotBlank String ticketCode,
            @NotEmpty List<@NotBlank String> scopes, @NotNull Instant expiresAt) {
    }

    public record MfaEnrollment(String secret, String otpauthUri) {
    }

    public record VerifyMfaRequest(@NotBlank @Size(min = 6, max = 8) String code) {
    }

    public record OidcConfigRequest(@NotBlank String issuer, @NotBlank String clientId,
            @NotBlank String clientSecretRef, @NotBlank String authorizationEndpoint,
            @NotBlank String tokenEndpoint, @NotBlank String jwksUri,
            @Email String emailDomain, boolean enabled) {
    }

    public record OidcStartData(String authorizationUrl, String state, Instant expiresAt) {
    }

    public record OidcCallbackRequest(@NotBlank String state, @NotBlank String code,
            @Size(max = 512) String deviceFingerprint) {
    }

    public record PlatformOverview(long activeTenants, long activeSubscriptions, long paidOrders,
            long openAlerts, long usageCostMinorUnits, long activeSupportGrants) {
    }
}
