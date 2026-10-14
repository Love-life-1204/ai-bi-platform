package com.aibi.platform.identity.service;

import com.aibi.platform.common.api.TraceIdContext;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 安全变更写入已有追加审计表；不保存令牌、密码或请求原文。 */
@Component
public class SecurityAudit {

    private final NamedParameterJdbcTemplate jdbc;
    private final ConcurrentLinkedDeque<Entry> events = new ConcurrentLinkedDeque<>();
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecurityAudit(@Qualifier("mysqlJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> provider) {
        this(provider.getIfAvailable());
    }

    public SecurityAudit(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static SecurityAudit inMemory() {
        return new SecurityAudit((NamedParameterJdbcTemplate) null);
    }

    public void record(long tenantId, long userId, long memberId, String action,
            String resourceType, long resourceId, long permissionVersion) {
        record(tenantId, userId, memberId, action, resourceType, resourceId, permissionVersion, "", "");
    }

    public void record(com.aibi.platform.identity.security.TokenService.TenantClaims actor,
            String action, String resourceType, long resourceId, long permissionVersion) {
        record(actor.tenantId(), actor.userId(), actor.memberId(), action, resourceType, resourceId, permissionVersion);
    }

    public void record(long tenantId, long userId, long memberId, String action,
            String resourceType, long resourceId, long permissionVersion, String keyHash, String bodyHash) {
        String trace = TraceIdContext.currentTraceId();
        if (trace == null || trace.isBlank() || "unknown".equals(trace)) {
            trace = java.util.UUID.randomUUID().toString();
        }
        var event = new Entry(random.nextLong(1, Long.MAX_VALUE), tenantId, userId, memberId,
                action, resourceType, resourceId, permissionVersion, trace, Instant.now(), keyHash, bodyHash);
        if (jdbc != null) {
            jdbc.update("""
                    INSERT INTO sys_audit_event
                        (id,tenant_id,actor_user_id,actor_member_id,action_code,resource_type,
                         resource_id,permission_version,trace_id,result_code,occurred_at,summary_json)
                    VALUES (:id,:tenant,:user,:member,:action,:type,:resource,:version,:trace,'SUCCESS',:now,:summary)
                    """, Map.ofEntries(Map.entry("id", event.id()), Map.entry("tenant", tenantId),
                    Map.entry("user", userId), Map.entry("member", memberId), Map.entry("action", action),
                    Map.entry("type", resourceType), Map.entry("resource", resourceId),
                    Map.entry("version", permissionVersion), Map.entry("trace", trace),
                    Map.entry("summary", "{\"onboardingKeyHash\":\"" + keyHash + "\",\"requestHash\":\"" + bodyHash + "\"}"),
                    Map.entry("now", Timestamp.from(event.occurredAt()))));
        } else {
            events.addLast(event);
            while (events.size() > 5000) {
                events.pollFirst();
            }
        }
    }

    public List<Entry> snapshot() {
        return List.copyOf(events);
    }

    public boolean matchesOnboarding(long tenantId, long userId, String keyHash, String bodyHash) {
        if (jdbc != null) {
            return !jdbc.query("""
                    SELECT id FROM sys_audit_event
                     WHERE tenant_id=:tenant AND actor_user_id=:user AND action_code='TENANT_CREATED'
                       AND JSON_UNQUOTE(JSON_EXTRACT(summary_json,'$.onboardingKeyHash'))=:keyHash
                       AND JSON_UNQUOTE(JSON_EXTRACT(summary_json,'$.requestHash'))=:bodyHash
                     FOR UPDATE
                    """, Map.of("tenant", tenantId, "user", userId, "keyHash", keyHash, "bodyHash", bodyHash),
                    (rs, row) -> rs.getLong("id")).isEmpty();
        }
        return events.stream().anyMatch(value -> value.tenantId() == tenantId && value.userId() == userId
                && "TENANT_CREATED".equals(value.action()) && keyHash.equals(value.keyHash())
                && bodyHash.equals(value.bodyHash()));
    }

    public record Entry(long id, long tenantId, long userId, long memberId, String action,
            String resourceType, long resourceId, long permissionVersion, String traceId, Instant occurredAt,
            String keyHash, String bodyHash) {
    }
}
