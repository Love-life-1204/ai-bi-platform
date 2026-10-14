package com.aibi.platform.commercial.service;

import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateAlertRuleRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateExportRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateOrderRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreatePlanRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateReportRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.CreateReportSubscriptionRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.EvaluateAlertRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ExportDownload;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelConfigRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelGatewayDecision;
import com.aibi.platform.commercial.dto.CommercialDtos.ModelGatewayRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.PaymentCallbackRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.PaymentResult;
import com.aibi.platform.commercial.dto.CommercialDtos.PlatformOverview;
import com.aibi.platform.commercial.dto.CommercialDtos.QuotaConfirmRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.ReconcileUsageRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.SupportGrantRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UpdateAlertRuleRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UpdateReportSubscriptionRequest;
import com.aibi.platform.commercial.dto.CommercialDtos.UsageRequest;
import com.aibi.platform.commercial.model.CommercialModels.AlertEvent;
import com.aibi.platform.commercial.model.CommercialModels.AlertRule;
import com.aibi.platform.commercial.model.CommercialModels.AuditEvent;
import com.aibi.platform.commercial.model.CommercialModels.ExportTask;
import com.aibi.platform.commercial.model.CommercialModels.ModelConfig;
import com.aibi.platform.commercial.model.CommercialModels.Notification;
import com.aibi.platform.commercial.model.CommercialModels.Order;
import com.aibi.platform.commercial.model.CommercialModels.PaymentEvent;
import com.aibi.platform.commercial.model.CommercialModels.PlanVersion;
import com.aibi.platform.commercial.model.CommercialModels.QuotaBalance;
import com.aibi.platform.commercial.model.CommercialModels.QuotaReservation;
import com.aibi.platform.commercial.model.CommercialModels.Refund;
import com.aibi.platform.commercial.model.CommercialModels.Report;
import com.aibi.platform.commercial.model.CommercialModels.ReportDelivery;
import com.aibi.platform.commercial.model.CommercialModels.ReportSubscription;
import com.aibi.platform.commercial.model.CommercialModels.Subscription;
import com.aibi.platform.commercial.model.CommercialModels.SupportGrant;
import com.aibi.platform.commercial.model.CommercialModels.UsageEntry;
import com.aibi.platform.commercial.model.CommercialModels.UsageSummary;
import com.aibi.platform.commercial.store.CommercialStore;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.exception.UnauthorizedException;
import com.aibi.platform.identity.entity.MemberEntity;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.security.TokenService.TenantClaims;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.messaging.TaskDispatcher;
import com.aibi.platform.messaging.TaskKind;
import com.aibi.platform.messaging.LocalTaskDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CommercialService {

    private static final String HMAC = "HmacSHA256";
    private final CommercialStore store;
    private final CommercialProperties properties;
    private final PermissionContextService permissions;
    private final MemberMapper members;
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService notificationDelivery;
    private final TaskDispatcher taskDispatcher;
    private final ThreadPoolExecutor exportExecutor;

    @Autowired
    public CommercialService(CommercialStore store, CommercialProperties properties,
            PermissionContextService permissions, MemberMapper members, ObjectMapper objectMapper,
            NotificationDeliveryService notificationDelivery, TaskDispatcher taskDispatcher) {
        this.store = store;
        this.properties = properties;
        this.permissions = permissions;
        this.members = members;
        this.objectMapper = objectMapper;
        this.notificationDelivery = notificationDelivery;
        this.taskDispatcher = taskDispatcher;
        int workers = Math.max(1, properties.getExportWorkers());
        this.exportExecutor = new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getExportQueueCapacity())),
                runnable -> {
                    Thread thread = new Thread(runnable, "commercial-export-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CommercialService(CommercialStore store, CommercialProperties properties,
            PermissionContextService permissions, MemberMapper members, ObjectMapper objectMapper,
            NotificationDeliveryService notificationDelivery) {
        this(store, properties, permissions, members, objectMapper,
                notificationDelivery, new LocalTaskDispatcher());
    }

    public PlanVersion createPlanVersion(CreatePlanRequest request) {
        return store.locked(0, () -> {
            String code = request.planCode().trim().toUpperCase(Locale.ROOT);
            int version = store.plans().values().stream()
                    .filter(value -> value.planCode().equals(code))
                    .mapToInt(PlanVersion::version).max().orElse(0) + 1;
            PlanVersion plan = new PlanVersion(store.nextId(), code, request.planName(), version,
                    request.priceMinorUnits(), request.currency().toUpperCase(Locale.ROOT),
                    request.billingPeriod().toUpperCase(Locale.ROOT), Map.copyOf(request.quotas()),
                    "ACTIVE", request.effectiveFrom() == null
                            ? Instant.now() : request.effectiveFrom());
            store.plans().put(plan.id(), plan);
            return plan;
        });
    }

    public List<PlanVersion> plans() {
        return store.plans().values().stream().filter(value -> "ACTIVE".equals(value.status()))
                .filter(value -> !value.effectiveFrom().isAfter(Instant.now()))
                .sorted(Comparator.comparing(PlanVersion::planCode)
                        .thenComparing(PlanVersion::version).reversed())
                .toList();
    }

    public Subscription currentSubscription(String authorization, long tenantId) {
        require(authorization, tenantId);
        return currentSubscriptionInternal(tenantId);
    }

    public Order createOrder(String authorization, long tenantId, String idempotencyKey,
            CreateOrderRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        requireTenantOwner(claims, tenantId);
        requireKey(idempotencyKey);
        PlanVersion plan = requiredPlan(request.planVersionId());
        String key = "order:" + tenantId + ":" + claims.memberId() + ":" + idempotencyKey;
        return store.locked(tenantId, () -> {
            Object replay = store.idempotency().get(key);
            if (replay instanceof Order order) {
                if (order.planVersionId() != request.planVersionId()) {
                    throw new ConflictException("幂等键已用于其他订单请求");
                }
                return store.orders().getOrDefault(order.id(), order);
            }
            Instant now = Instant.now();
            Subscription subscription = new Subscription(store.nextId(), tenantId, plan.id(),
                    "PENDING", now, periodEnd(now, plan.billingPeriod()), false);
            Order order = new Order(store.nextId(), tenantId, subscription.id(), orderNo(),
                    plan.id(), plan.priceMinorUnits(), plan.currency(), "PENDING", null, now,
                    idempotencyKey);
            store.subscriptions().put(subscription.id(), subscription);
            store.orders().put(order.id(), order);
            store.idempotency().put(key, order);
            audit(tenantId, claims, "ORDER_CREATED", "ORDER", String.valueOf(order.id()),
                    objectMapper.valueToTree(Map.of("planVersionId", plan.id())), "SUCCESS");
            return order;
        });
    }

    public List<Order> orders(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.orders().values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(Order::createdAt).reversed()).toList();
    }

    public Order order(String authorization, long tenantId, long orderId) {
        require(authorization, tenantId);
        return requiredOrder(tenantId, orderId);
    }

    public PaymentResult paymentCallback(String provider, String timestamp, String signature,
            byte[] rawBody) {
        verifyPaymentSignature(timestamp, signature, rawBody);
        PaymentCallbackRequest request;
        try {
            request = objectMapper.readValue(rawBody, PaymentCallbackRequest.class);
        } catch (IOException exception) {
            throw new BadRequestException("支付回调请求体无效");
        }
        String eventKey = provider + ":" + request.eventId();
        String payloadHash = sha256(rawBody);
        return store.locked(request.tenantId(), () -> {
            PaymentEvent previous = store.payments().get(eventKey);
            if (previous != null) {
                if (!previous.payloadHash().equals(payloadHash)) {
                    throw new ConflictException("支付事件编号与原始请求体冲突");
                }
                return new PaymentResult(request.eventId(), "PROCESSED", true);
            }
            Order order = requiredOrder(request.tenantId(), request.orderId());
            validatePaymentAmount(order, request);
            Instant now = Instant.now();
            PaymentEvent event = new PaymentEvent(provider, request.eventId(), request.eventType(),
                    request.tenantId(), order.id(), request.amountMinorUnits(), request.currency(),
                    payloadHash, now, now);
            applyPayment(order, request, now);
            store.payments().put(eventKey, event);
            audit(request.tenantId(), null, "PAYMENT_CALLBACK", "ORDER",
                    String.valueOf(order.id()), objectMapper.valueToTree(Map.of(
                            "provider", provider, "eventType", request.eventType())), "SUCCESS");
            return new PaymentResult(request.eventId(), "PROCESSED", false);
        });
    }

    public QuotaBalance initializeQuota(long tenantId, String quotaCode, long limit,
            Instant windowStart, Instant windowEnd) {
        if (limit < 0 || !windowEnd.isAfter(windowStart)) {
            throw new BadRequestException("配额窗口或上限无效");
        }
        return store.locked(tenantId, () -> {
            QuotaBalance value = new QuotaBalance(tenantId, quotaCode, limit, 0, 0,
                    windowStart, windowEnd);
            store.quotas().put(quotaKey(tenantId, quotaCode), value);
            return value;
        });
    }

    public QuotaReservation reserve(long tenantId, String quotaCode, String reservationKey,
            long amount) {
        if (amount <= 0) {
            throw new BadRequestException("预留配额必须大于 0");
        }
        String key = tenantId + ":" + reservationKey;
        return store.locked(tenantId, () -> {
            QuotaReservation existing = store.reservations().get(key);
            if (existing != null) {
                if (!existing.quotaCode().equals(quotaCode) || existing.reservedAmount() != amount) {
                    throw new ConflictException("配额预留键冲突");
                }
                return existing;
            }
            QuotaBalance balance = requiredQuota(tenantId, quotaCode);
            if (balance.remaining() < amount) {
                throw new ApiException(ApiErrorCode.QUOTA_EXCEEDED);
            }
            QuotaBalance updated = new QuotaBalance(tenantId, quotaCode, balance.limit(),
                    balance.used(), balance.reserved() + amount, balance.windowStart(),
                    balance.windowEnd());
            QuotaReservation reservation = new QuotaReservation(reservationKey, tenantId,
                    quotaCode, amount, 0, "RESERVED", Instant.now());
            store.quotas().put(quotaKey(tenantId, quotaCode), updated);
            store.reservations().put(key, reservation);
            return reservation;
        });
    }

    public QuotaReservation confirm(long tenantId, String reservationKey,
            QuotaConfirmRequest request) {
        String key = tenantId + ":" + reservationKey;
        String eventKey = "quota-confirm:" + tenantId + ":" + request.businessEventKey();
        return store.locked(tenantId, () -> {
            Object replay = store.idempotency().get(eventKey);
            if (replay instanceof QuotaReservation reservation) {
                if (!reservation.reservationKey().equals(reservationKey)
                        || reservation.confirmedAmount() != request.actualAmount()) {
                    throw new ConflictException("配额确认事件键冲突");
                }
                return reservation;
            }
            QuotaReservation current = requiredReservation(key);
            if (!"RESERVED".equals(current.status())) {
                throw new ConflictException("配额预留已终结");
            }
            QuotaBalance balance = requiredQuota(tenantId, current.quotaCode());
            long availableAfterRelease = balance.remaining() + current.reservedAmount();
            if (request.actualAmount() > availableAfterRelease) {
                throw new ApiException(ApiErrorCode.QUOTA_EXCEEDED);
            }
            QuotaBalance updatedBalance = new QuotaBalance(tenantId, current.quotaCode(),
                    balance.limit(), balance.used() + request.actualAmount(),
                    balance.reserved() - current.reservedAmount(), balance.windowStart(),
                    balance.windowEnd());
            QuotaReservation updated = new QuotaReservation(current.reservationKey(), tenantId,
                    current.quotaCode(), current.reservedAmount(), request.actualAmount(),
                    "CONFIRMED", current.createdAt());
            store.quotas().put(quotaKey(tenantId, current.quotaCode()), updatedBalance);
            store.reservations().put(key, updated);
            store.idempotency().put(eventKey, updated);
            return updated;
        });
    }

    public QuotaReservation release(long tenantId, String reservationKey, String eventKey) {
        String key = tenantId + ":" + reservationKey;
        String idempotencyKey = "quota-release:" + tenantId + ":" + eventKey;
        return store.locked(tenantId, () -> {
            Object replay = store.idempotency().get(idempotencyKey);
            if (replay instanceof QuotaReservation reservation) {
                if (!reservation.reservationKey().equals(reservationKey)) {
                    throw new ConflictException("配额释放事件键冲突");
                }
                return reservation;
            }
            QuotaReservation current = requiredReservation(key);
            if (!"RESERVED".equals(current.status())) {
                throw new ConflictException("配额预留已终结");
            }
            QuotaBalance balance = requiredQuota(tenantId, current.quotaCode());
            QuotaBalance updatedBalance = new QuotaBalance(tenantId, current.quotaCode(),
                    balance.limit(), balance.used(),
                    balance.reserved() - current.reservedAmount(), balance.windowStart(),
                    balance.windowEnd());
            QuotaReservation updated = new QuotaReservation(current.reservationKey(), tenantId,
                    current.quotaCode(), current.reservedAmount(), 0, "RELEASED",
                    current.createdAt());
            store.quotas().put(quotaKey(tenantId, current.quotaCode()), updatedBalance);
            store.reservations().put(key, updated);
            store.idempotency().put(idempotencyKey, updated);
            return updated;
        });
    }

    public List<QuotaBalance> quotas(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.quotas().values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(QuotaBalance::quotaCode)).toList();
    }

    public String reserveAiRequest(long tenantId, long taskId) {
        if (!properties.isQuotaEnforcementEnabled()) {
            return null;
        }
        String key = "ai-task-" + taskId;
        reserve(tenantId, "AI_REQUEST", key, 1);
        return key;
    }

    public void settleAiRequest(long tenantId, long taskId, boolean consumed) {
        if (!properties.isQuotaEnforcementEnabled()) {
            return;
        }
        String key = "ai-task-" + taskId;
        if (consumed) {
            confirm(tenantId, key, new QuotaConfirmRequest("ai-settle-" + taskId, 1));
        } else {
            release(tenantId, key, "ai-release-" + taskId);
        }
    }

    public ModelConfig configureModel(String authorization, long tenantId,
            ModelConfigRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        requireTenantOwner(claims, tenantId);
        String policy = request.dataPolicy().toUpperCase(Locale.ROOT);
        if (!List.of("DISABLED", "AGGREGATE_ONLY", "REDACTED", "APPROVED_DETAIL").contains(policy)) {
            throw new BadRequestException("不支持的数据外发策略");
        }
        ModelConfig value = new ModelConfig(tenantId, request.providerCode(), request.modelName(),
                request.region(), policy, request.externalEnabled(), Map.copyOf(request.prices()));
        store.models().put(tenantId, value);
        audit(tenantId, claims, "MODEL_POLICY_CHANGED", "MODEL_CONFIG", String.valueOf(tenantId),
                objectMapper.valueToTree(Map.of("policy", policy, "region", request.region())),
                "SUCCESS");
        return value;
    }

    public ModelGatewayDecision modelDecision(String authorization, long tenantId,
            ModelGatewayRequest request) {
        require(authorization, tenantId);
        ModelConfig config = store.models().get(tenantId);
        if (config == null || !config.externalEnabled() || "DISABLED".equals(config.dataPolicy())) {
            return new ModelGatewayDecision(false, null, null, "DISABLED",
                    "租户未启用外部模型");
        }
        String classification = request.dataClassification().toUpperCase(Locale.ROOT);
        boolean detail = "DETAIL".equals(classification) || "SENSITIVE".equals(classification);
        boolean allowed = !detail || "APPROVED_DETAIL".equals(config.dataPolicy());
        if ("SENSITIVE".equals(classification)) {
            allowed = false;
        }
        return new ModelGatewayDecision(allowed, config.providerCode(), config.modelName(),
                config.dataPolicy(), allowed ? "ALLOWED" : "DATA_POLICY_DENIED");
    }

    public UsageEntry recordUsage(long tenantId, UsageRequest request) {
        String key = tenantId + ":" + request.providerCode() + ":" + request.providerRequestId();
        UsageEntry existing = store.usage().get(key);
        if (existing != null) {
            return existing;
        }
        ModelConfig config = store.models().get(tenantId);
        long inputRate = config == null ? 0 : config.prices().getOrDefault("promptPerMillion", 0L);
        long outputRate = config == null ? 0 : config.prices().getOrDefault("completionPerMillion", 0L);
        long cost = roundedCost(request.promptTokens(), inputRate)
                + roundedCost(request.completionTokens(), outputRate);
        UsageEntry entry = new UsageEntry(tenantId, request.taskId(), request.providerCode(),
                request.providerRequestId(), request.modelName(), request.promptTokens(),
                request.completionTokens(), cost,
                request.currency() == null ? "CNY" : request.currency(), true, Instant.now());
        UsageEntry raced = store.usage().putIfAbsent(key, entry);
        return raced == null ? entry : raced;
    }

    public UsageSummary usage(String authorization, long tenantId) {
        require(authorization, tenantId);
        List<UsageEntry> entries = store.usage().values().stream()
                .filter(value -> value.tenantId() == tenantId).toList();
        return new UsageSummary(tenantId,
                entries.stream().mapToLong(UsageEntry::promptTokens).sum(),
                entries.stream().mapToLong(UsageEntry::completionTokens).sum(),
                entries.stream().mapToLong(UsageEntry::amountMinorUnits).sum(),
                entries.stream().findFirst().map(UsageEntry::currency).orElse("CNY"),
                entries.size(), store.reconciliation().getOrDefault(tenantId, 0L));
    }

    public UsageSummary reconcile(String authorization, long tenantId,
            ReconcileUsageRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        requireTenantOwner(claims, tenantId);
        long estimated = store.usage().values().stream()
                .filter(value -> value.tenantId() == tenantId)
                .filter(value -> value.providerCode().equals(request.providerCode()))
                .filter(value -> request.from() == null || !value.occurredAt().isBefore(request.from()))
                .filter(value -> request.to() == null || value.occurredAt().isBefore(request.to()))
                .mapToLong(UsageEntry::amountMinorUnits).sum();
        store.reconciliation().put(tenantId, request.providerAmountMinorUnits() - estimated);
        return usage(authorization, tenantId);
    }

    public Report createReport(String authorization, long tenantId, CreateReportRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        request.rows().forEach(row -> {
            if (row.size() != request.columns().size()) {
                throw new BadRequestException("报告行列数与表头不一致");
            }
        });
        Report report = new Report(store.nextId(), tenantId, request.workspaceId(),
                claims.memberId(), request.name(), List.copyOf(request.columns()),
                request.rows().stream().map(List::copyOf).toList(), "SAVED", Instant.now());
        store.reports().put(report.id(), report);
        return report;
    }

    public List<Report> reports(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.reports().values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(Report::createdAt).reversed()).toList();
    }

    public Report report(String authorization, long tenantId, long reportId) {
        require(authorization, tenantId);
        return requiredReport(tenantId, reportId);
    }

    public ExportTask createExport(String authorization, long tenantId, String idempotencyKey,
            CreateExportRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        requireKey(idempotencyKey);
        requiredReport(tenantId, request.reportId());
        String format = request.format().toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(format)) {
            throw new BadRequestException("导出格式仅支持 CSV/XLSX/PDF");
        }
        String key = "export:" + tenantId + ":" + claims.memberId() + ":" + idempotencyKey;
        return store.locked(tenantId, () -> {
            Object replay = store.idempotency().get(key);
            if (replay instanceof ExportTask task) {
                if (task.reportId() != request.reportId() || !task.format().equals(format)) {
                    throw new ConflictException("导出幂等键冲突");
                }
                return store.exports().getOrDefault(task.id(), task);
            }
            ExportTask task = new ExportTask(store.nextId(), tenantId, claims.memberId(),
                    request.reportId(), format, claims.memberPermissionVersion(), "PENDING", null,
                    null, null, null, null, Instant.now());
            try {
                taskDispatcher.dispatch(TaskKind.EXPORT, tenantId, task.id(), () -> {
                    reserveExport(tenantId, task.id());
                    store.exports().put(task.id(), task);
                    store.idempotency().put(key, task);
                }, () -> exportExecutor.execute(() -> generateExport(task)));
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                ExportTask failed = task.withResult("FAILED", "EXPORT_QUEUE_FULL", null,
                        null, null, null);
                store.exports().put(task.id(), failed);
                settleExport(tenantId, task.id(), false);
                throw new ApiException(ApiErrorCode.TOO_MANY_REQUESTS);
            }
            return task;
        });
    }

    public ExportTask export(String authorization, long tenantId, long exportId) {
        TenantClaims claims = require(authorization, tenantId);
        return authorizedExport(claims, tenantId, exportId);
    }

    public void consumeExport(long tenantId, long exportId) {
        ExportTask task = store.exports().get(exportId);
        if (task == null || task.tenantId() != tenantId || !"PENDING".equals(task.status())) {
            return;
        }
        generateExport(task);
    }

    public ExportTask cancelExport(String authorization, long tenantId, long exportId) {
        TenantClaims claims = require(authorization, tenantId);
        ExportTask task = authorizedExport(claims, tenantId, exportId);
        if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(task.status())) {
            return task;
        }
        ExportTask cancelled = task.withResult("CANCELLED", null, null, null, null, null);
        store.exports().put(task.id(), cancelled);
        settleExport(tenantId, task.id(), false);
        return cancelled;
    }

    public ExportDownload download(String authorization, long tenantId, long exportId) {
        TenantClaims claims = require(authorization, tenantId);
        ExportTask task = authorizedExport(claims, tenantId, exportId);
        if (!"SUCCEEDED".equals(task.status()) || task.content() == null) {
            throw new ConflictException("导出文件尚未就绪");
        }
        if (task.expiresAt() == null || !task.expiresAt().isAfter(Instant.now())) {
            throw new NotFoundException("导出文件已过期");
        }
        return new ExportDownload(task.fileName(), task.contentType(), task.content().clone());
    }

    public ReportSubscription createReportSubscription(String authorization, long tenantId,
            CreateReportSubscriptionRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        requiredReport(tenantId, request.reportId());
        request.recipientMemberIds().forEach(id -> requiredActiveMember(tenantId, id));
        ReportSubscription value = new ReportSubscription(store.nextId(), tenantId,
                request.reportId(), claims.memberId(), request.schedule(), request.timezone(),
                List.copyOf(request.recipientMemberIds()), "ACTIVE",
                request.nextRunAt() == null ? Instant.now().plus(1, ChronoUnit.DAYS)
                        : request.nextRunAt(), Instant.now());
        store.reportSubscriptions().put(value.id(), value);
        return value;
    }

    public List<ReportSubscription> reportSubscriptions(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.reportSubscriptions().values().stream()
                .filter(value -> value.tenantId() == tenantId).toList();
    }

    public ReportSubscription updateReportSubscription(String authorization, long tenantId,
            long subscriptionId, UpdateReportSubscriptionRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        ReportSubscription current = requiredReportSubscription(tenantId, subscriptionId);
        if (claims.memberId() != current.creatorMemberId()) {
            throw new ForbiddenException();
        }
        List<Long> recipients = request.recipientMemberIds() == null
                ? current.recipientMemberIds() : List.copyOf(request.recipientMemberIds());
        recipients.forEach(id -> requiredActiveMember(tenantId, id));
        ReportSubscription updated = new ReportSubscription(current.id(), tenantId,
                current.reportId(), current.creatorMemberId(),
                request.schedule() == null ? current.schedule() : request.schedule(),
                request.timezone() == null ? current.timezone() : request.timezone(), recipients,
                request.status() == null ? current.status() : request.status(),
                request.nextRunAt() == null ? current.nextRunAt() : request.nextRunAt(),
                current.createdAt());
        store.reportSubscriptions().put(updated.id(), updated);
        return updated;
    }

    public List<ReportDelivery> runDueSubscriptions(Instant now) {
        List<ReportDelivery> created = new ArrayList<>();
        store.reportSubscriptions().values().stream()
                .filter(value -> "ACTIVE".equals(value.status()))
                .filter(value -> !value.nextRunAt().isAfter(now))
                .forEach(subscription -> {
                    for (long recipientId : subscription.recipientMemberIds()) {
                        MemberEntity member = members.findById(subscription.tenantId(), recipientId)
                                .orElse(null);
                        String status = member != null && "ACTIVE".equals(member.memberStatus())
                                ? "AUTHORIZED" : "SKIPPED";
                        String error = "AUTHORIZED".equals(status) ? null : "RECIPIENT_FORBIDDEN";
                        ReportDelivery delivery = new ReportDelivery(store.nextId(),
                                subscription.tenantId(), subscription.id(), recipientId,
                                member == null ? 0 : member.permissionVersion(), status, error,
                                subscription.nextRunAt(), "AUTHORIZED".equals(status) ? now : null);
                        store.deliveries().put(delivery.id(), delivery);
                        created.add(delivery);
                        if ("AUTHORIZED".equals(status)) {
                            notificationDelivery.deliver(subscription.tenantId(), recipientId,
                                    "定时报告已生成", "报告订阅 " + subscription.id()
                                            + " 已完成收件人权限复核并生成。");
                        }
                    }
                    ReportSubscription next = new ReportSubscription(subscription.id(),
                            subscription.tenantId(), subscription.reportId(),
                            subscription.creatorMemberId(), subscription.schedule(),
                            subscription.timezone(), subscription.recipientMemberIds(),
                            subscription.status(), nextSchedule(subscription.nextRunAt()),
                            subscription.createdAt());
                    store.reportSubscriptions().put(next.id(), next);
                });
        return created;
    }

    public AlertRule createAlertRule(String authorization, long tenantId,
            CreateAlertRuleRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        validateOperator(request.operator());
        request.recipients().forEach(id -> requiredActiveMember(tenantId, id));
        long suppression = request.suppressionSeconds() == null
                ? properties.getAlertSuppressionSeconds() : request.suppressionSeconds();
        AlertRule rule = new AlertRule(store.nextId(), tenantId, request.workspaceId(),
                request.name(), request.operator(), request.threshold(), claims.memberId(),
                List.copyOf(request.recipients()), "ACTIVE", suppression, Instant.now());
        store.alertRules().put(rule.id(), rule);
        return rule;
    }

    public List<AlertRule> alertRules(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.alertRules().values().stream().filter(value -> value.tenantId() == tenantId)
                .toList();
    }

    public AlertRule updateAlertRule(String authorization, long tenantId, long ruleId,
            UpdateAlertRuleRequest request) {
        TenantClaims claims = require(authorization, tenantId);
        AlertRule current = requiredAlertRule(tenantId, ruleId);
        if (claims.memberId() != current.ownerMemberId()) {
            throw new ForbiddenException();
        }
        String operator = request.operator() == null ? current.operator() : request.operator();
        validateOperator(operator);
        List<Long> recipients = request.recipients() == null
                ? current.recipients() : List.copyOf(request.recipients());
        AlertRule updated = new AlertRule(current.id(), tenantId, current.workspaceId(),
                request.name() == null ? current.name() : request.name(), operator,
                request.threshold() == null ? current.threshold() : request.threshold(),
                current.ownerMemberId(), recipients,
                request.status() == null ? current.status() : request.status(),
                request.suppressionSeconds() == null ? current.suppressionSeconds()
                        : request.suppressionSeconds(), current.createdAt());
        store.alertRules().put(updated.id(), updated);
        return updated;
    }

    public AlertEvent evaluateAlert(long tenantId, long ruleId, EvaluateAlertRequest request) {
        AlertRule rule = requiredAlertRule(tenantId, ruleId);
        if (!"ACTIVE".equals(rule.status()) || !matches(rule.operator(), request.value(),
                rule.threshold())) {
            return null;
        }
        String dedupe = tenantId + ":" + ruleId + ":" + request.evaluationWindow();
        return store.locked(tenantId, () -> {
            AlertEvent duplicate = store.alertEvents().values().stream()
                    .filter(value -> value.dedupeKey().equals(dedupe)).findFirst().orElse(null);
            if (duplicate != null) {
                return duplicate;
            }
            Instant now = Instant.now();
            boolean suppressed = store.alertEvents().values().stream()
                    .filter(value -> value.tenantId() == tenantId && value.ruleId() == ruleId)
                    .filter(value -> "OPEN".equals(value.status()))
                    .anyMatch(value -> value.occurredAt().plusSeconds(rule.suppressionSeconds())
                            .isAfter(now));
            AlertEvent event = new AlertEvent(store.nextId(), tenantId, ruleId, dedupe,
                    request.value(), request.evidence(), suppressed ? "SUPPRESSED" : "OPEN", now,
                    null);
            store.alertEvents().put(event.id(), event);
            if (!suppressed) {
                notifyRecipients(rule, event);
            }
            return event;
        });
    }

    public List<AlertEvent> alertEvents(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.alertEvents().values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(AlertEvent::occurredAt).reversed()).toList();
    }

    public AlertEvent acknowledgeAlert(String authorization, long tenantId, long eventId) {
        require(authorization, tenantId);
        AlertEvent current = store.alertEvents().get(eventId);
        if (current == null || current.tenantId() != tenantId) {
            throw new NotFoundException("告警事件不存在");
        }
        AlertEvent updated = new AlertEvent(current.id(), tenantId, current.ruleId(),
                current.dedupeKey(), current.actualValue(), current.evidence(), "ACKNOWLEDGED",
                current.occurredAt(), Instant.now());
        store.alertEvents().put(updated.id(), updated);
        return updated;
    }

    public List<Notification> notifications(String authorization, long tenantId) {
        TenantClaims claims = require(authorization, tenantId);
        return store.notifications().values().stream()
                .filter(value -> value.tenantId() == tenantId
                        && value.recipientMemberId() == claims.memberId())
                .sorted(Comparator.comparing(Notification::createdAt).reversed()).toList();
    }

    public Notification readNotification(String authorization, long tenantId, long notificationId) {
        TenantClaims claims = require(authorization, tenantId);
        Notification current = store.notifications().get(notificationId);
        if (current == null || current.tenantId() != tenantId
                || current.recipientMemberId() != claims.memberId()) {
            throw new NotFoundException("通知不存在");
        }
        Notification updated = new Notification(current.id(), tenantId,
                current.recipientMemberId(), current.dedupeKey(), current.type(), current.title(),
                current.summary(), Instant.now(), current.createdAt());
        store.notifications().put(updated.id(), updated);
        return updated;
    }

    public List<AuditEvent> audits(String authorization, long tenantId) {
        require(authorization, tenantId);
        return store.audits().values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed()).toList();
    }

    public PlatformOverview overview() {
        Instant now = Instant.now();
        return new PlatformOverview(
                store.subscriptions().values().stream().map(Subscription::tenantId).distinct().count(),
                store.subscriptions().values().stream()
                        .filter(value -> "ACTIVE".equals(value.status())).count(),
                store.orders().values().stream().filter(value -> "PAID".equals(value.status())).count(),
                store.alertEvents().values().stream().filter(value -> "OPEN".equals(value.status())).count(),
                store.usage().values().stream().mapToLong(UsageEntry::amountMinorUnits).sum(),
                store.supportGrants().values().stream()
                        .filter(value -> value.revokedAt() == null && value.expiresAt().isAfter(now)).count());
    }

    public SupportGrant createSupportGrant(SupportGrantRequest request) {
        Instant now = Instant.now();
        if (!request.expiresAt().isAfter(now)
                || request.expiresAt().isAfter(now.plusSeconds(properties.getSupportGrantMaxSeconds()))) {
            throw new BadRequestException("支持授权过期时间必须在允许窗口内");
        }
        requiredActiveMember(request.tenantId(), request.approverMemberId());
        SupportGrant grant = new SupportGrant(store.nextId(), request.tenantId(),
                request.approverMemberId(), request.supportUserId(), request.ticketCode(),
                List.copyOf(request.scopes()), request.expiresAt(), null);
        store.supportGrants().put(grant.id(), grant);
        audit(request.tenantId(), null, "SUPPORT_GRANT_CREATED", "SUPPORT_GRANT",
                String.valueOf(grant.id()), objectMapper.valueToTree(Map.of(
                        "ticketCode", request.ticketCode(), "scopes", request.scopes())), "SUCCESS");
        return grant;
    }

    public List<SupportGrant> supportGrants() {
        return store.supportGrants().values().stream()
                .sorted(Comparator.comparing(SupportGrant::expiresAt).reversed()).toList();
    }

    private void applyPayment(Order order, PaymentCallbackRequest request, Instant now) {
        String type = request.eventType().toUpperCase(Locale.ROOT);
        if ("PAYMENT_SUCCEEDED".equals(type)) {
            if (!"PAID".equals(order.status())) {
                store.orders().put(order.id(), order.withStatus("PAID", now));
                Subscription subscription = store.subscriptions().get(order.subscriptionId());
                Subscription active = new Subscription(subscription.id(), subscription.tenantId(),
                        subscription.planVersionId(), "ACTIVE", now, subscription.periodEnd(), false);
                store.subscriptions().put(active.id(), active);
                activatePlanQuotas(active, requiredPlan(active.planVersionId()));
            }
        } else if ("PAYMENT_FAILED".equals(type)) {
            store.orders().put(order.id(), order.withStatus("FAILED", null));
        } else if ("REFUND_SUCCEEDED".equals(type)) {
            if (request.refundId() == null || request.refundId().isBlank()) {
                throw new BadRequestException("退款事件缺少 refundId");
            }
            boolean duplicate = store.refunds().values().stream().anyMatch(value ->
                    value.providerRefundId().equals(request.refundId()));
            if (!duplicate) {
                long alreadyRefunded = store.refunds().values().stream()
                        .filter(value -> value.orderId() == order.id())
                        .mapToLong(Refund::amountMinorUnits).sum();
                if (alreadyRefunded + request.amountMinorUnits() > order.amountMinorUnits()) {
                    throw new ConflictException("累计退款金额超过订单金额");
                }
                Refund refund = new Refund(store.nextId(), order.tenantId(), order.id(),
                        request.refundId(), request.amountMinorUnits(), request.currency(),
                        "SUCCEEDED", now);
                store.refunds().put(refund.id(), refund);
                long refunded = store.refunds().values().stream()
                        .filter(value -> value.orderId() == order.id())
                        .mapToLong(Refund::amountMinorUnits).sum();
                if (refunded >= order.amountMinorUnits()) {
                    store.orders().put(order.id(), order.withStatus("REFUNDED", order.paidAt()));
                }
            }
        } else {
            throw new BadRequestException("不支持的支付事件类型");
        }
    }

    private void activatePlanQuotas(Subscription subscription, PlanVersion plan) {
        plan.quotas().forEach((code, limit) -> initializeQuota(subscription.tenantId(), code,
                limit, subscription.periodStart(), subscription.periodEnd()));
    }

    private void validatePaymentAmount(Order order, PaymentCallbackRequest request) {
        if (!order.currency().equalsIgnoreCase(request.currency())) {
            throw new ConflictException("支付回调币种与订单不一致");
        }
        if (!"REFUND_SUCCEEDED".equalsIgnoreCase(request.eventType())
                && order.amountMinorUnits() != request.amountMinorUnits()) {
            throw new ConflictException("支付回调金额与订单不一致");
        }
        if ("REFUND_SUCCEEDED".equalsIgnoreCase(request.eventType())
                && request.amountMinorUnits() > order.amountMinorUnits()) {
            throw new ConflictException("退款金额超过订单金额");
        }
    }

    private void verifyPaymentSignature(String timestamp, String signature, byte[] rawBody) {
        try {
            long epoch = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - epoch)
                    > properties.getPaymentClockSkewSeconds()) {
                throw new UnauthorizedException();
            }
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(properties.getPaymentWebhookSecret()
                    .getBytes(StandardCharsets.UTF_8), HMAC));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            if (signature == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII))) {
                throw new UnauthorizedException();
            }
        } catch (NumberFormatException | GeneralSecurityException exception) {
            throw new UnauthorizedException();
        }
    }

    private void generateExport(ExportTask task) {
        ExportTask current = store.exports().get(task.id());
        if (current == null || "CANCELLED".equals(current.status())) {
            return;
        }
        MemberEntity member = members.findById(task.tenantId(), task.requesterMemberId())
                .filter(value -> "ACTIVE".equals(value.memberStatus()))
                .orElse(null);
        if (member == null || member.permissionVersion() != task.permissionVersion()) {
            store.exports().put(task.id(), task.withResult("FAILED", "PERMISSION_REVOKED",
                    null, null, null, null));
            settleExport(task.tenantId(), task.id(), false);
            return;
        }
        try {
            Report report = requiredReport(task.tenantId(), task.reportId());
            byte[] bytes = switch (task.format()) {
                case "CSV" -> csv(report);
                case "XLSX" -> xlsx(report);
                case "PDF" -> pdf(report);
                default -> throw new IllegalStateException("Unsupported export format");
            };
            ExportTask latest = store.exports().get(task.id());
            if (latest != null && !"CANCELLED".equals(latest.status())) {
                String extension = task.format().toLowerCase(Locale.ROOT);
                String contentType = switch (task.format()) {
                    case "CSV" -> "text/csv;charset=UTF-8";
                    case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    default -> "application/pdf";
                };
                store.exports().put(task.id(), task.withResult("SUCCEEDED", null, bytes,
                        "report-" + task.reportId() + "." + extension, contentType,
                        Instant.now().plusSeconds(properties.getExportRetentionSeconds())));
                settleExport(task.tenantId(), task.id(), true);
            }
        } catch (RuntimeException exception) {
            store.exports().put(task.id(), task.withResult("FAILED", "EXPORT_FAILED",
                    null, null, null, null));
            settleExport(task.tenantId(), task.id(), false);
        }
    }

    private void reserveExport(long tenantId, long exportId) {
        if (properties.isQuotaEnforcementEnabled()) {
            reserve(tenantId, "EXPORT", "export-task-" + exportId, 1);
        }
    }

    private void settleExport(long tenantId, long exportId, boolean consumed) {
        if (!properties.isQuotaEnforcementEnabled()) {
            return;
        }
        String key = "export-task-" + exportId;
        try {
            if (consumed) {
                confirm(tenantId, key, new QuotaConfirmRequest("export-settle-" + exportId, 1));
            } else {
                release(tenantId, key, "export-release-" + exportId);
            }
        } catch (ConflictException ignored) {
            // A cancellation racing a worker may settle first; the quota state machine is final.
        }
    }

    private byte[] csv(Report report) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(0xef);
            output.write(0xbb);
            output.write(0xbf);
            try (CSVPrinter printer = new CSVPrinter(
                    new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT)) {
                printer.printRecord(report.columns());
                for (List<String> row : report.rows()) {
                    printer.printRecord(row.stream().map(this::safeSpreadsheetValue).toList());
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 CSV 失败", exception);
        }
    }

    private byte[] xlsx(Report report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("报告");
            Row header = sheet.createRow(0);
            for (int index = 0; index < report.columns().size(); index++) {
                header.createCell(index).setCellValue(report.columns().get(index));
            }
            for (int rowIndex = 0; rowIndex < report.rows().size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> values = report.rows().get(rowIndex);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(safeSpreadsheetValue(values.get(column)));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 XLSX 失败", exception);
        }
    }

    private byte[] pdf(Report report) {
        String text = report.name() + "\n" + String.join(" | ", report.columns()) + "\n"
                + report.rows().stream().map(row -> String.join(" | ", row))
                        .reduce("", (left, right) -> left + right + "\n");
        String ascii = text.replaceAll("[^\\x20-\\x7E\\r\\n]", "?");
        String stream = "BT /F1 10 Tf 40 780 Td ("
                + ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                        .replace("\n", ") Tj 0 -14 Td (") + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length
                        + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(output.size());
                output.write((index + 1 + " 0 obj\n" + objects.get(index)
                        + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
            }
            int xref = output.size();
            output.write(("xref\n0 " + offsets.size() + "\n0000000000 65535 f \n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int index = 1; index < offsets.size(); index++) {
                output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(index))
                        .getBytes(StandardCharsets.US_ASCII));
            }
            output.write(("trailer << /Size " + offsets.size() + " /Root 1 0 R >>\nstartxref\n"
                    + xref + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成 PDF 失败", exception);
        }
    }

    private String safeSpreadsheetValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                ? "'" + value : value;
    }

    private void notifyRecipients(AlertRule rule, AlertEvent event) {
        for (long recipient : rule.recipients()) {
            if (members.findById(rule.tenantId(), recipient)
                    .filter(value -> "ACTIVE".equals(value.memberStatus())).isEmpty()) {
                continue;
            }
            String dedupe = event.dedupeKey() + ":" + recipient;
            boolean exists = store.notifications().values().stream()
                    .anyMatch(value -> value.dedupeKey().equals(dedupe));
            if (!exists) {
                Notification notification = new Notification(store.nextId(), rule.tenantId(),
                        recipient, dedupe, "ALERT", rule.name(),
                        "当前值 " + event.actualValue() + " 已触发阈值", null, Instant.now());
                store.notifications().put(notification.id(), notification);
                notificationDelivery.deliver(rule.tenantId(), recipient, rule.name(),
                        notification.summary());
            }
        }
    }

    private TenantClaims require(String authorization, long tenantId) {
        return permissions.requireContext(authorization, tenantId);
    }

    private void requireTenantOwner(TenantClaims claims, long tenantId) {
        MemberEntity member = requiredActiveMember(tenantId, claims.memberId());
        if (!member.owner()) {
            throw new ForbiddenException();
        }
    }

    private PlanVersion requiredPlan(long id) {
        PlanVersion plan = store.plans().get(id);
        if (plan == null || !"ACTIVE".equals(plan.status())) {
            throw new NotFoundException("套餐版本不存在");
        }
        return plan;
    }

    private Subscription currentSubscriptionInternal(long tenantId) {
        return store.subscriptions().values().stream()
                .filter(value -> value.tenantId() == tenantId)
                .filter(value -> List.of("ACTIVE", "TRIAL", "PAST_DUE").contains(value.status()))
                .max(Comparator.comparing(Subscription::periodStart))
                .orElseThrow(() -> new NotFoundException("租户尚无有效订阅"));
    }

    private Order requiredOrder(long tenantId, long orderId) {
        Order order = store.orders().get(orderId);
        if (order == null || order.tenantId() != tenantId) {
            throw new NotFoundException("订单不存在");
        }
        return order;
    }

    private QuotaBalance requiredQuota(long tenantId, String quotaCode) {
        QuotaBalance balance = store.quotas().get(quotaKey(tenantId, quotaCode));
        if (balance == null || !balance.windowEnd().isAfter(Instant.now())) {
            throw new ApiException(ApiErrorCode.QUOTA_EXCEEDED, "租户配额未开通或已过期");
        }
        return balance;
    }

    private QuotaReservation requiredReservation(String key) {
        QuotaReservation value = store.reservations().get(key);
        if (value == null) {
            throw new NotFoundException("配额预留不存在");
        }
        return value;
    }

    private Report requiredReport(long tenantId, long reportId) {
        Report report = store.reports().get(reportId);
        if (report == null || report.tenantId() != tenantId) {
            throw new NotFoundException("报告不存在");
        }
        return report;
    }

    private ExportTask authorizedExport(TenantClaims claims, long tenantId, long exportId) {
        ExportTask task = store.exports().get(exportId);
        if (task == null || task.tenantId() != tenantId
                || task.requesterMemberId() != claims.memberId()) {
            throw new NotFoundException("导出任务不存在");
        }
        if (task.permissionVersion() != claims.memberPermissionVersion()) {
            throw new ForbiddenException();
        }
        return task;
    }

    private ReportSubscription requiredReportSubscription(long tenantId, long subscriptionId) {
        ReportSubscription value = store.reportSubscriptions().get(subscriptionId);
        if (value == null || value.tenantId() != tenantId) {
            throw new NotFoundException("报告订阅不存在");
        }
        return value;
    }

    private AlertRule requiredAlertRule(long tenantId, long ruleId) {
        AlertRule value = store.alertRules().get(ruleId);
        if (value == null || value.tenantId() != tenantId) {
            throw new NotFoundException("告警规则不存在");
        }
        return value;
    }

    private MemberEntity requiredActiveMember(long tenantId, long memberId) {
        return members.findById(tenantId, memberId)
                .filter(value -> "ACTIVE".equals(value.memberStatus()))
                .orElseThrow(ForbiddenException::new);
    }

    private void requireKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
            throw new BadRequestException("Idempotency-Key 必须为 8—128 位安全字符");
        }
    }

    private String quotaKey(long tenantId, String quotaCode) {
        return tenantId + ":" + quotaCode.toUpperCase(Locale.ROOT);
    }

    private String orderNo() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private Instant periodEnd(Instant start, String period) {
        return "YEAR".equalsIgnoreCase(period) ? start.plus(365, ChronoUnit.DAYS)
                : start.plus(30, ChronoUnit.DAYS);
    }

    private Instant nextSchedule(Instant previous) {
        return previous.plus(1, ChronoUnit.DAYS);
    }

    private long roundedCost(long tokens, long ratePerMillion) {
        if (tokens == 0 || ratePerMillion == 0) {
            return 0;
        }
        return Math.addExact(Math.multiplyExact(tokens, ratePerMillion), 999_999) / 1_000_000;
    }

    private void validateOperator(String operator) {
        if (!List.of("GT", "GTE", "LT", "LTE", "EQ").contains(operator.toUpperCase(Locale.ROOT))) {
            throw new BadRequestException("不支持的告警比较符");
        }
    }

    private boolean matches(String operator, double value, double threshold) {
        return switch (operator.toUpperCase(Locale.ROOT)) {
            case "GT" -> value > threshold;
            case "GTE" -> value >= threshold;
            case "LT" -> value < threshold;
            case "LTE" -> value <= threshold;
            case "EQ" -> Double.compare(value, threshold) == 0;
            default -> false;
        };
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private void audit(long tenantId, TenantClaims claims, String action, String resourceType,
            String resourceId, JsonNode summary, String result) {
        AuditEvent event = new AuditEvent(store.nextId(), tenantId,
                claims == null ? null : claims.userId(), claims == null ? null : claims.memberId(),
                action, resourceType, resourceId, result, summary, Instant.now());
        store.audits().put(event.id(), event);
    }

    @PreDestroy
    public void close() {
        exportExecutor.shutdownNow();
    }
}
