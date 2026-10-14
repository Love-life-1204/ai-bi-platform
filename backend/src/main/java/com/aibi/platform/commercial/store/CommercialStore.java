package com.aibi.platform.commercial.store;

import com.aibi.platform.commercial.model.CommercialModels.AlertEvent;
import com.aibi.platform.commercial.model.CommercialModels.AlertRule;
import com.aibi.platform.commercial.model.CommercialModels.AuditEvent;
import com.aibi.platform.commercial.model.CommercialModels.ExportTask;
import com.aibi.platform.commercial.model.CommercialModels.ModelConfig;
import com.aibi.platform.commercial.model.CommercialModels.Notification;
import com.aibi.platform.commercial.model.CommercialModels.Order;
import com.aibi.platform.commercial.model.CommercialModels.OidcAuthorization;
import com.aibi.platform.commercial.model.CommercialModels.OidcProvider;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Profile-neutral commercial store: in-memory locally and durable/transactional when MySQL is enabled. */
@Repository
public class CommercialStore {

    private final AtomicLong ids = new AtomicLong(50_000);
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, PlanVersion> plans;
    private final ConcurrentMap<Long, Subscription> subscriptions;
    private final ConcurrentMap<Long, Order> orders;
    private final ConcurrentMap<String, PaymentEvent> payments;
    private final ConcurrentMap<Long, Refund> refunds;
    private final ConcurrentMap<String, QuotaBalance> quotas;
    private final ConcurrentMap<String, QuotaReservation> reservations;
    private final ConcurrentMap<Long, ModelConfig> models;
    private final ConcurrentMap<String, UsageEntry> usage;
    private final ConcurrentMap<Long, Report> reports;
    private final ConcurrentMap<Long, ExportTask> exports;
    private final ConcurrentMap<Long, ReportSubscription> reportSubscriptions;
    private final ConcurrentMap<Long, ReportDelivery> deliveries;
    private final ConcurrentMap<Long, AlertRule> alertRules;
    private final ConcurrentMap<Long, AlertEvent> alertEvents;
    private final ConcurrentMap<Long, Notification> notifications;
    private final ConcurrentMap<Long, AuditEvent> audits;
    private final ConcurrentMap<Long, SupportGrant> supportGrants;
    private final ConcurrentMap<Long, OidcProvider> oidcProviders;
    private final ConcurrentMap<String, OidcAuthorization> oidcAuthorizations;
    private final ConcurrentMap<String, Object> idempotency;
    private final ConcurrentMap<Long, Long> reconciliation;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public CommercialStore() {
        this(null, null, new ObjectMapper());
    }

    @Autowired
    public CommercialStore(ObjectMapper objectMapper,
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> jdbcProvider,
            @Qualifier("mysqlTransactionManager")
            ObjectProvider<PlatformTransactionManager> transactionProvider,
            @Value("${app.datasource.enabled:false}") boolean databaseEnabled) {
        this(databaseEnabled ? jdbcProvider.getIfAvailable() : null,
                databaseEnabled ? transactionProvider.getIfAvailable() : null, objectMapper);
        if (databaseEnabled && (jdbc == null || transaction == null)) {
            throw new IllegalStateException("商业化持久化已启用但 MySQL 事务组件不可用");
        }
    }

    private CommercialStore(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        Map<String, Class<?>> allowed = allowedTypes();
        plans = map("plans", Long.class, PlanVersion.class, objectMapper, allowed);
        subscriptions = map("subscriptions", Long.class, Subscription.class, objectMapper, allowed);
        orders = map("orders", Long.class, Order.class, objectMapper, allowed);
        payments = map("payments", String.class, PaymentEvent.class, objectMapper, allowed);
        refunds = map("refunds", Long.class, Refund.class, objectMapper, allowed);
        quotas = map("quotas", String.class, QuotaBalance.class, objectMapper, allowed);
        reservations = map("reservations", String.class, QuotaReservation.class, objectMapper, allowed);
        models = map("models", Long.class, ModelConfig.class, objectMapper, allowed);
        usage = map("usage", String.class, UsageEntry.class, objectMapper, allowed);
        reports = map("reports", Long.class, Report.class, objectMapper, allowed);
        exports = map("exports", Long.class, ExportTask.class, objectMapper, allowed);
        reportSubscriptions = map("report-subscriptions", Long.class, ReportSubscription.class,
                objectMapper, allowed);
        deliveries = map("deliveries", Long.class, ReportDelivery.class, objectMapper, allowed);
        alertRules = map("alert-rules", Long.class, AlertRule.class, objectMapper, allowed);
        alertEvents = map("alert-events", Long.class, AlertEvent.class, objectMapper, allowed);
        notifications = map("notifications", Long.class, Notification.class, objectMapper, allowed);
        audits = map("audits", Long.class, AuditEvent.class, objectMapper, allowed);
        supportGrants = map("support-grants", Long.class, SupportGrant.class, objectMapper, allowed);
        oidcProviders = map("oidc-providers", Long.class, OidcProvider.class, objectMapper, allowed);
        oidcAuthorizations = map("oidc-authorizations", String.class, OidcAuthorization.class,
                objectMapper, allowed);
        idempotency = map("idempotency", String.class, Object.class, objectMapper, allowed);
        reconciliation = map("reconciliation", Long.class, Long.class, objectMapper, allowed);
    }

    public long nextId() {
        return jdbc == null ? ids.incrementAndGet() : random.nextLong(1, Long.MAX_VALUE);
    }

    public <T> T locked(long tenantId, Supplier<T> action) {
        synchronized (locks.computeIfAbsent(tenantId, ignored -> new Object())) {
            if (transaction == null) {
                return action.get();
            }
            return transaction.execute(status -> {
                jdbc.update("INSERT IGNORE INTO sys_commercial_lock (lock_key) VALUES (:key)",
                        Map.of("key", tenantId));
                jdbc.queryForObject("SELECT lock_key FROM sys_commercial_lock"
                        + " WHERE lock_key = :key FOR UPDATE", Map.of("key", tenantId), Long.class);
                return action.get();
            });
        }
    }

    public ConcurrentMap<Long, PlanVersion> plans() {
        return plans;
    }

    public ConcurrentMap<Long, Subscription> subscriptions() {
        return subscriptions;
    }

    public ConcurrentMap<Long, Order> orders() {
        return orders;
    }

    public ConcurrentMap<String, PaymentEvent> payments() {
        return payments;
    }

    public ConcurrentMap<Long, Refund> refunds() {
        return refunds;
    }

    public ConcurrentMap<String, QuotaBalance> quotas() {
        return quotas;
    }

    public ConcurrentMap<String, QuotaReservation> reservations() {
        return reservations;
    }

    public ConcurrentMap<Long, ModelConfig> models() {
        return models;
    }

    public ConcurrentMap<String, UsageEntry> usage() {
        return usage;
    }

    public ConcurrentMap<Long, Report> reports() {
        return reports;
    }

    public ConcurrentMap<Long, ExportTask> exports() {
        return exports;
    }

    public ConcurrentMap<Long, ReportSubscription> reportSubscriptions() {
        return reportSubscriptions;
    }

    public ConcurrentMap<Long, ReportDelivery> deliveries() {
        return deliveries;
    }

    public ConcurrentMap<Long, AlertRule> alertRules() {
        return alertRules;
    }

    public ConcurrentMap<Long, AlertEvent> alertEvents() {
        return alertEvents;
    }

    public ConcurrentMap<Long, Notification> notifications() {
        return notifications;
    }

    public ConcurrentMap<Long, AuditEvent> audits() {
        return audits;
    }

    public ConcurrentMap<Long, SupportGrant> supportGrants() {
        return supportGrants;
    }

    public ConcurrentMap<String, Object> idempotency() {
        return idempotency;
    }

    public ConcurrentMap<Long, Long> reconciliation() {
        return reconciliation;
    }

    public ConcurrentMap<Long, OidcProvider> oidcProviders() {
        return oidcProviders;
    }

    public ConcurrentMap<String, OidcAuthorization> oidcAuthorizations() {
        return oidcAuthorizations;
    }

    private <K, V> ConcurrentMap<K, V> map(String namespace, Class<K> keyType,
            Class<V> valueType, ObjectMapper objectMapper, Map<String, Class<?>> allowed) {
        if (jdbc == null) {
            return new ConcurrentHashMap<>();
        }
        return new JdbcJsonConcurrentMap<>(namespace, keyType, valueType, jdbc, objectMapper, allowed);
    }

    private Map<String, Class<?>> allowedTypes() {
        return Map.ofEntries(
                Map.entry(Order.class.getName(), Order.class),
                Map.entry(QuotaReservation.class.getName(), QuotaReservation.class),
                Map.entry(ExportTask.class.getName(), ExportTask.class));
    }
}
