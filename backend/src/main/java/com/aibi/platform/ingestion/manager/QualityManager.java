package com.aibi.platform.ingestion.manager;

import com.aibi.platform.ingestion.dto.IngestionDtos.QualityResultData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRunData;
import com.aibi.platform.ingestion.entity.BatchEntity;
import com.aibi.platform.ingestion.entity.QualityRuleEntity;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class QualityManager {

    private static final String PASSED = "PASSED";
    private final IngestionMapper mapper;

    public QualityManager(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    public QualityRunData validate(long tenantId, BatchEntity batch) {
        List<StagedRowEntity> rows = mapper.listBatchRows(tenantId, batch.id());
        List<QualityResultData> results = new ArrayList<>();
        for (QualityRuleEntity rule : mapper.listQualityRules(tenantId, batch.datasetId())) {
            results.add(evaluate(rule, rows));
        }
        results.add(reconciliationResult(batch, rows));
        boolean blocked = results.stream().anyMatch(result -> "FAILED".equals(result.status())
                && isBlocking(tenantId, batch.datasetId(), result.ruleId()));
        String status = blocked ? "FAILED" : PASSED;
        mapper.saveQualityResults(tenantId, batch.id(), results);
        return new QualityRunData(batch.id(), status, results);
    }

    public ReconciledValues reconcile(List<StagedRowEntity> rows) {
        Map<String, StagedRowEntity> latest = new HashMap<>();
        for (StagedRowEntity row : rows) {
            latest.merge(row.eventKey().businessKey(), row, this::newer);
        }
        long businessKeys = latest.values().stream().filter(row -> !row.deleted()).count();
        BigDecimal amount = latest.values().stream().filter(row -> !row.deleted())
                .map(StagedRowEntity::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReconciledValues(rows.size(), businessKeys, amount);
    }

    private QualityResultData evaluate(QualityRuleEntity rule, List<StagedRowEntity> rows) {
        long failed = switch (rule.ruleType()) {
            case "REQUIRED" -> rows.stream().filter(row -> empty(row.values().get(rule.field()))).count();
            case "UNIQUE" -> duplicateCount(rows, rule.field());
            case "TYPE" -> rows.stream().filter(row -> !matchesType(row.values().get(rule.field()),
                    rule.expected())).count();
            case "RANGE" -> rows.stream().filter(row -> !inRange(row.values().get(rule.field()),
                    rule.minimum(), rule.maximum())).count();
            default -> rows.size();
        };
        return new QualityResultData(rule.id(), rule.ruleCode(), failed == 0 ? PASSED : "FAILED",
                failed, Long.toString(failed), expected(rule));
    }

    private QualityResultData reconciliationResult(BatchEntity batch, List<StagedRowEntity> rows) {
        ReconciledValues values = reconcile(rows);
        boolean passed = batch.expectedRows() == values.rows()
                && batch.sourceBusinessKeys() == values.businessKeys()
                && batch.sourceAmount().compareTo(values.amount()) == 0;
        String observed = "rows=" + values.rows() + ",keys=" + values.businessKeys()
                + ",amount=" + values.amount().toPlainString();
        String expected = "rows=" + batch.expectedRows() + ",keys=" + batch.sourceBusinessKeys()
                + ",amount=" + batch.sourceAmount().toPlainString();
        return new QualityResultData(0, "SYSTEM_RECONCILIATION", passed ? PASSED : "FAILED",
                passed ? 0 : 1, observed, expected);
    }

    private long duplicateCount(List<StagedRowEntity> rows, String field) {
        Set<Object> values = new HashSet<>();
        long duplicates = 0;
        for (StagedRowEntity row : rows) {
            Object value = row.values().get(field);
            if (!empty(value) && !values.add(value)) {
                duplicates++;
            }
        }
        return duplicates;
    }

    private boolean matchesType(Object value, String expected) {
        if (value == null) {
            return true;
        }
        return switch (expected == null ? "" : expected) {
            case "STRING" -> value instanceof String;
            case "LONG" -> value instanceof Long || value instanceof Integer;
            case "DECIMAL" -> value instanceof BigDecimal;
            case "BOOLEAN" -> value instanceof Boolean;
            case "DATE" -> value instanceof java.time.LocalDate;
            case "DATETIME" -> value instanceof java.time.Instant;
            default -> false;
        };
    }

    private boolean inRange(Object value, String minimum, String maximum) {
        if (value == null) {
            return true;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.toString());
            return (minimum == null || minimum.isBlank()
                    || decimal.compareTo(new BigDecimal(minimum)) >= 0)
                    && (maximum == null || maximum.isBlank()
                    || decimal.compareTo(new BigDecimal(maximum)) <= 0);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isBlocking(long tenantId, long datasetId, long ruleId) {
        if (ruleId == 0) {
            return true;
        }
        return mapper.listQualityRules(tenantId, datasetId).stream()
                .filter(rule -> rule.id() == ruleId)
                .anyMatch(rule -> "BLOCK".equals(rule.severity()));
    }

    private String expected(QualityRuleEntity rule) {
        return switch (rule.ruleType()) {
            case "REQUIRED" -> "non-null";
            case "UNIQUE" -> "unique";
            case "TYPE" -> rule.expected();
            case "RANGE" -> "[" + rule.minimum() + "," + rule.maximum() + "]";
            default -> "unsupported";
        };
    }

    private boolean empty(Object value) {
        return value == null || value.toString().isBlank();
    }

    private StagedRowEntity newer(StagedRowEntity left, StagedRowEntity right) {
        int time = left.updatedAt().compareTo(right.updatedAt());
        if (time != 0) {
            return time > 0 ? left : right;
        }
        return left.eventKey().sourceVersion().compareTo(right.eventKey().sourceVersion()) >= 0
                ? left : right;
    }

    public record ReconciledValues(long rows, long businessKeys, BigDecimal amount) {
    }
}
