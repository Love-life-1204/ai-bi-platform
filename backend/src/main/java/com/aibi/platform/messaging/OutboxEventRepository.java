package com.aibi.platform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class OutboxEventRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final AtomicLong tenantCursor = new AtomicLong();

    public OutboxEventRepository(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactionManager,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void insert(TaskEnvelope envelope) {
        jdbc.update("""
                INSERT INTO sys_outbox_event
                    (id,tenant_id,event_key,aggregate_type,aggregate_id,event_type,
                     schema_version,payload_json,event_status,retry_count,available_at,
                     created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:eventKey,'ASYNC_TASK',:taskId,:eventType,
                        :schemaVersion,:payload,'PENDING',0,NOW(3),NOW(3),NOW(3),0)
                """, new MapSqlParameterSource()
                .addValue("id", positiveId())
                .addValue("tenantId", envelope.tenantId())
                .addValue("eventKey", envelope.eventId())
                .addValue("taskId", envelope.taskId())
                .addValue("eventType", "task." + envelope.kind().code() + ".requested")
                .addValue("schemaVersion", envelope.schemaVersion())
                .addValue("payload", write(envelope)));
    }

    public List<OutboxEvent> claim(String owner, int limit, Duration lease) {
        List<OutboxEvent> claimed = transactions.execute(status -> {
            int candidateLimit = Math.min(1000, Math.max(limit, limit * 8));
            List<OutboxEvent> candidates = jdbc.query("""
                    SELECT id,event_key,payload_json,retry_count
                      FROM sys_outbox_event
                     WHERE deleted_at IS NULL
                       AND ((event_status IN ('PENDING','RETRY') AND available_at<=NOW(3))
                         OR (event_status='PUBLISHING' AND lease_until<NOW(3)))
                     ORDER BY available_at,id
                     LIMIT :candidateLimit
                     FOR UPDATE SKIP LOCKED
                    """, Map.of("candidateLimit", candidateLimit), this::event);
            List<OutboxEvent> events = selectFairly(
                    candidates, limit, tenantCursor.getAndIncrement());
            Instant leaseUntil = Instant.now().plus(lease);
            for (OutboxEvent event : events) {
                jdbc.update("""
                        UPDATE sys_outbox_event
                           SET event_status='PUBLISHING',lease_owner=:owner,
                               lease_until=:leaseUntil,updated_at=NOW(3),
                               lock_version=lock_version+1
                         WHERE id=:id
                        """, new MapSqlParameterSource("owner", owner)
                        .addValue("leaseUntil", Timestamp.from(leaseUntil))
                        .addValue("id", event.id()));
            }
            return events;
        });
        return claimed == null ? List.of() : claimed;
    }

    static List<OutboxEvent> selectFairly(
            List<OutboxEvent> candidates, int limit, long cursor) {
        if (candidates.size() <= 1 || limit <= 0) {
            return candidates.stream().limit(Math.max(0, limit)).toList();
        }
        Map<Long, ArrayDeque<OutboxEvent>> perTenant = new LinkedHashMap<>();
        for (OutboxEvent event : candidates) {
            perTenant.computeIfAbsent(event.envelope().tenantId(), ignored -> new ArrayDeque<>())
                    .addLast(event);
        }
        List<ArrayDeque<OutboxEvent>> lanes = new ArrayList<>(perTenant.values());
        int start = Math.floorMod(cursor, lanes.size());
        List<OutboxEvent> selected = new ArrayList<>(Math.min(limit, candidates.size()));
        while (selected.size() < limit) {
            boolean found = false;
            for (int step = 0; step < lanes.size() && selected.size() < limit; step++) {
                OutboxEvent event = lanes.get((start + step) % lanes.size()).pollFirst();
                if (event != null) {
                    selected.add(event);
                    found = true;
                }
            }
            if (!found) {
                break;
            }
            start = (start + 1) % lanes.size();
        }
        return selected;
    }

    public void markPublished(long id, String owner) {
        jdbc.update("""
                UPDATE sys_outbox_event
                   SET event_status='PUBLISHED',published_at=NOW(3),lease_owner=NULL,
                       lease_until=NULL,updated_at=NOW(3),lock_version=lock_version+1
                 WHERE id=:id AND lease_owner=:owner AND event_status='PUBLISHING'
                """, Map.of("id", id, "owner", owner));
    }

    public void markFailed(long id, String owner, int retryCount,
            int maxAttempts, Instant availableAt) {
        String nextStatus = retryCount >= maxAttempts ? "DEAD" : "RETRY";
        jdbc.update("""
                UPDATE sys_outbox_event
                   SET event_status=:status,retry_count=:retryCount,available_at=:availableAt,
                       lease_owner=NULL,lease_until=NULL,updated_at=NOW(3),
                       lock_version=lock_version+1
                 WHERE id=:id AND lease_owner=:owner AND event_status='PUBLISHING'
                """, new MapSqlParameterSource("status", nextStatus)
                .addValue("retryCount", retryCount)
                .addValue("availableAt", Timestamp.from(availableAt))
                .addValue("id", id).addValue("owner", owner));
    }

    private OutboxEvent event(ResultSet resultSet, int row) throws SQLException {
        try {
            TaskEnvelope envelope = json.readValue(
                    resultSet.getString("payload_json"), TaskEnvelope.class);
            return new OutboxEvent(resultSet.getLong("id"),
                    resultSet.getString("event_key"), envelope,
                    resultSet.getInt("retry_count"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Outbox payload 无法反序列化", exception);
        }
    }

    private String write(TaskEnvelope envelope) {
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox payload 无法序列化", exception);
        }
    }

    private long positiveId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
