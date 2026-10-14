package com.aibi.platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import com.aibi.platform.ingestion.dto.IngestionDtos.LeaseClaim;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.ingestion.entity.SyncRunEntity;
import com.aibi.platform.ingestion.manager.FieldMappingManager;
import com.aibi.platform.ingestion.manager.IngestionProperties;
import com.aibi.platform.ingestion.manager.QualityManager;
import com.aibi.platform.ingestion.manager.SourceTableReader;
import com.aibi.platform.ingestion.manager.SyncWorkerManager;
import com.aibi.platform.ingestion.mapper.InMemoryIngestionMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyncCorrectnessTest {

    @Test
    void shouldFenceExpiredWorkerAndKeepUnpublishedReplayInvisible() {
        InMemoryIngestionMapper mapper = new InMemoryIngestionMapper();
        Instant created = Instant.parse("2026-10-10T00:00:00Z");
        mapper.insertRun(new SyncRunEntity(1, 7, 8, 1, "request", "PENDING",
                null, null, 0, null, null, 0, false, null, null, null, created));
        LeaseClaim first = mapper.claimLease(7, 1, "worker-a", created,
                Duration.ofSeconds(1)).orElseThrow();
        LeaseClaim second = mapper.claimLease(7, 1, "worker-b", created.plusSeconds(2),
                Duration.ofSeconds(10)).orElseThrow();
        assertNotEquals(first.fencingToken(), second.fencingToken());
        assertFalse(mapper.renewLease(7, 1, "worker-a", first.fencingToken(),
                created.plusSeconds(2), Duration.ofSeconds(10)));

        IdempotentEventKey baselineKey = new IdempotentEventKey(7, 9, 3, 100, 1,
                "A", "1:A");
        assertTrue(mapper.insertEvent(new StagedRowEntity(baselineKey, 87,
                second.fencingToken(), Map.of("amount", new BigDecimal("100")),
                new BigDecimal("100"), created, false)));
        mapper.publishCas(7, 9, 0, 100, 1, 1, 87);

        IdempotentEventKey key = new IdempotentEventKey(7, 9, 3, 100, 2, "A", "2:A");
        StagedRowEntity interrupted = new StagedRowEntity(key, 88, second.fencingToken(),
                Map.of("amount", new BigDecimal("120")), new BigDecimal("120"),
                created.plusSeconds(2), false);
        assertTrue(mapper.insertEvent(interrupted));
        assertFalse(mapper.insertEvent(interrupted));
        IdempotentEventKey otherSourceKey = new IdempotentEventKey(7, 9, 4, 100, 2,
                "A", "2:A");
        assertTrue(mapper.insertEvent(new StagedRowEntity(otherSourceKey, 89,
                second.fencingToken(), interrupted.values(), interrupted.amount(),
                interrupted.updatedAt(), interrupted.deleted())));
        List<StagedRowEntity> visible = mapper.listVisibleRows(7, 9, 100, 1);
        assertEquals(1, visible.size());
        assertEquals(0, new BigDecimal("100").compareTo(visible.get(0).amount()));
    }

    @Test
    void shouldHandleReplayLateUpdateAndDeleteWithoutDoubleCountingAmount() {
        InMemoryIngestionMapper mapper = new InMemoryIngestionMapper();
        ScenarioReader reader = new ScenarioReader();
        FieldMappingManager fieldMapping = new FieldMappingManager();
        QualityManager quality = new QualityManager(mapper);
        IngestionProperties properties = new IngestionProperties();
        properties.setLeaseSeconds(60);
        properties.setMaxAttempts(1);
        SyncWorkerManager worker = new SyncWorkerManager(mapper, reader, fieldMapping,
                quality, properties, org.mockito.Mockito.mock(com.aibi.platform.ingestion.service.IngestionTaskAuthorization.class));
        try {
            Instant now = Instant.parse("2026-10-10T00:00:00Z");
            SyncJobEntity full = job("FULL", null, 0, now);
            mapper.insertSyncJob(full);
            mapper.insertRun(run(11, 1, "full", now));
            worker.execute(5, 11);
            assertEquals("SUCCESS", mapper.findRun(5, 11).orElseThrow().status());
            assertEquals(1, mapper.publication(5, 9).committedPublishSeq());

            SyncJobEntity current = mapper.findSyncJob(5, 1).orElseThrow();
            SyncJobEntity incremental = new SyncJobEntity(current.id(), current.tenantId(),
                    current.datasourceId(), current.datasetId(), current.jobCode(), "INCREMENTAL",
                    current.sourceSchema(), current.sourceTable(), current.primaryKeyField(),
                    "updated_at", current.amountField(), current.mappings(), current.batchSize(),
                    null, "UTC", "ENABLED", Instant.parse("2026-10-10T00:00:00Z"), "",
                    current.lockVersion() + 1, current.createdAt(), now.plusSeconds(1));
            mapper.updateSyncJob(incremental, current.lockVersion());
            mapper.insertRun(run(12, 1, "incremental", now.plusSeconds(1)));
            worker.execute(5, 12);

            assertEquals("SUCCESS", mapper.findRun(5, 12).orElseThrow().status());
            var publication = mapper.publication(5, 9);
            assertEquals(2, publication.committedPublishSeq());
            List<StagedRowEntity> visible = mapper.listVisibleRows(5, 9,
                    publication.activeGeneration(), publication.committedPublishSeq());
            var values = quality.reconcile(visible);
            assertEquals(1, values.businessKeys());
            assertEquals(0, new BigDecimal("120").compareTo(values.amount()));
            assertEquals(4, visible.size());
        } finally {
            worker.close();
        }
    }

    @Test
    void shouldDeadLetterPermanentFailureWithoutRetrying() {
        InMemoryIngestionMapper mapper = new InMemoryIngestionMapper();
        ScenarioReader reader = new ScenarioReader();
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxAttempts(3);
        SyncWorkerManager worker = new SyncWorkerManager(mapper, reader,
                new FieldMappingManager(), new QualityManager(mapper), properties,
                org.mockito.Mockito.mock(com.aibi.platform.ingestion.service.IngestionTaskAuthorization.class));
        try {
            Instant now = Instant.parse("2026-10-10T00:00:00Z");
            SyncJobEntity enabled = job("FULL", null, 0, now);
            SyncJobEntity disabled = new SyncJobEntity(enabled.id(), enabled.tenantId(),
                    enabled.datasourceId(), enabled.datasetId(), enabled.jobCode(),
                    enabled.syncMode(), enabled.sourceSchema(), enabled.sourceTable(),
                    enabled.primaryKeyField(), enabled.updatedAtField(), enabled.amountField(),
                    enabled.mappings(), enabled.batchSize(), enabled.scheduleExpression(),
                    enabled.timezone(), "DISABLED", enabled.watermarkTime(),
                    enabled.watermarkPrimaryKey(), enabled.lockVersion(), enabled.createdAt(), now);
            mapper.insertSyncJob(disabled);
            mapper.insertRun(run(21, 1, "disabled", now));

            worker.execute(5, 21);

            SyncRunEntity failed = mapper.findRun(5, 21).orElseThrow();
            assertEquals("FAILED", failed.status());
            assertTrue(failed.deadLetter());
            assertEquals(1, mapper.listRuns(5, 1L).size());
        } finally {
            worker.close();
        }
    }

    private SyncJobEntity job(String mode, Instant watermark, long version, Instant now) {
        return new SyncJobEntity(1, 5, 6, 9, "orders", mode, "sales", "orders",
                "order_id", mode.equals("INCREMENTAL") ? "updated_at" : null, "amount",
                List.of(new FieldMapping("order_id", "order_id", "STRING", null, true),
                        new FieldMapping("amount", "amount", "DECIMAL", null, true)),
                100, null, "UTC", "ENABLED", watermark, null, version, now, now);
    }

    private SyncRunEntity run(long id, long jobId, String key, Instant now) {
        return new SyncRunEntity(id, 5, jobId, 1, key, "PENDING", null, null,
                0, null, null, 0, false, null, null, null, now);
    }

    private static final class ScenarioReader implements SourceTableReader {

        @Override
        public SourcePage read(SyncJobEntity job, Instant lastTime, String lastKey, int limit) {
            if ("FULL".equals(job.syncMode())) {
                if (lastKey != null) {
                    return new SourcePage(List.of(), false);
                }
                return new SourcePage(List.of(
                        row("A", "100", "2026-10-10T00:00:00Z", false),
                        row("B", "50", "2026-10-10T00:00:00Z", false)), false);
            }
            if (lastTime != null && lastTime.isAfter(Instant.parse("2026-10-10T00:00:00Z"))) {
                return new SourcePage(List.of(), false);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row("A", "120", "2026-10-10T01:00:00Z", false));
            rows.add(row("A", "120", "2026-10-10T01:00:00Z", false));
            rows.add(row("B", "50", "2026-10-10T02:00:00Z", true));
            return new SourcePage(rows, false);
        }

        private Map<String, Object> row(String id, String amount, String updated, boolean deleted) {
            return Map.of("order_id", id, "amount", amount, "updated_at", Instant.parse(updated),
                    "is_deleted", deleted);
        }
    }
}
