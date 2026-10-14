package com.aibi.platform.commercial.model;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CommercialModels {

    private CommercialModels() {
    }

    public record PlanVersion(@JsonResourceId long id, String planCode, String planName, int version,
            long priceMinorUnits, String currency, String billingPeriod,
            Map<String, Long> quotas, String status, Instant effectiveFrom) {
    }

    public record Subscription(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long planVersionId, String status,
            Instant periodStart, Instant periodEnd, boolean cancelAtPeriodEnd) {
    }

    public record Order(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long subscriptionId, String orderNo,
            @JsonResourceId long planVersionId, long amountMinorUnits, String currency, String status,
            Instant paidAt, Instant createdAt, String idempotencyKey) {
        public Order withStatus(String nextStatus, Instant nextPaidAt) {
            return new Order(id, tenantId, subscriptionId, orderNo, planVersionId,
                    amountMinorUnits, currency, nextStatus, nextPaidAt, createdAt, idempotencyKey);
        }
    }

    public record PaymentEvent(String provider, String eventId, String eventType, @JsonResourceId long tenantId,
            @JsonResourceId long orderId, long amountMinorUnits, String currency, String payloadHash,
            Instant verifiedAt, Instant processedAt) {
    }

    public record Refund(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long orderId, String providerRefundId,
            long amountMinorUnits, String currency, String status, Instant createdAt) {
    }

    public record QuotaBalance(@JsonResourceId long tenantId, String quotaCode, long limit, long used,
            long reserved, Instant windowStart, Instant windowEnd) {
        public long remaining() {
            return limit - used - reserved;
        }
    }

    public record QuotaReservation(String reservationKey, @JsonResourceId long tenantId, String quotaCode,
            long reservedAmount, long confirmedAmount, String status, Instant createdAt) {
    }

    public record ModelConfig(@JsonResourceId long tenantId, String providerCode, String modelName,
            String region, String dataPolicy, boolean externalEnabled, Map<String, Long> prices) {
    }

    public record UsageEntry(@JsonResourceId long tenantId, @JsonResourceId long taskId, String providerCode,
            String providerRequestId, String modelName, long promptTokens, long completionTokens,
            long amountMinorUnits, String currency, boolean estimated, Instant occurredAt) {
    }

    public record UsageSummary(@JsonResourceId long tenantId, long promptTokens, long completionTokens,
            long amountMinorUnits, String currency, long entryCount, long reconciliationDelta) {
    }

    public record Report(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, @JsonResourceId long ownerMemberId,
            String name, List<String> columns, List<List<String>> rows, String status,
            Instant createdAt) {
    }

    public record ExportTask(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long requesterMemberId, @JsonResourceId long reportId,
            String format, long permissionVersion, String status, String errorCode,
            byte[] content, String fileName, String contentType, Instant expiresAt,
            Instant createdAt) {
        public ExportTask withResult(String nextStatus, String nextError, byte[] nextContent,
                String nextFileName, String nextContentType, Instant nextExpiresAt) {
            return new ExportTask(id, tenantId, requesterMemberId, reportId, format,
                    permissionVersion, nextStatus, nextError, nextContent, nextFileName,
                    nextContentType, nextExpiresAt, createdAt);
        }
    }

    public record ReportSubscription(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long reportId, @JsonResourceId long creatorMemberId,
            String schedule, String timezone, @JsonResourceIds List<Long> recipientMemberIds, String status,
            Instant nextRunAt, Instant createdAt) {
    }

    public record ReportDelivery(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long subscriptionId,
            @JsonResourceId long recipientMemberId, long permissionVersion, String status, String errorCode,
            Instant scheduledAt, Instant deliveredAt) {
    }

    public record AlertRule(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String name,
            String operator, double threshold, @JsonResourceId long ownerMemberId, @JsonResourceIds List<Long> recipients,
            String status, long suppressionSeconds, Instant createdAt) {
    }

    public record AlertEvent(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long ruleId, String dedupeKey,
            double actualValue, JsonNode evidence, String status, Instant occurredAt,
            Instant acknowledgedAt) {
    }

    public record Notification(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long recipientMemberId, String dedupeKey,
            String type, String title, String summary, Instant readAt, Instant createdAt) {
    }

    public record AuditEvent(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId Long actorUserId, @JsonResourceId Long actorMemberId,
            String action, String resourceType, String resourceId, String result,
            JsonNode summary, Instant occurredAt) {
    }

    public record SupportGrant(@JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long approverMemberId, @JsonResourceId long supportUserId,
            String ticketCode, List<String> scopes, Instant expiresAt, Instant revokedAt) {
    }

    public record OidcProvider(@JsonResourceId long tenantId, String issuer, String clientId,
            String clientSecretRef, String authorizationEndpoint, String tokenEndpoint,
            String jwksUri, String emailDomain, boolean enabled) {
    }

    public record OidcAuthorization(@JsonResourceId long tenantId, String redirectUri, String nonce,
            String codeVerifier, Instant expiresAt) {
    }
}
