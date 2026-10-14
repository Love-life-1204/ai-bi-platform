package com.aibi.platform.ingestion.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import com.aibi.platform.ingestion.dto.IngestionDtos.MappingResult;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FieldMappingManager {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final Set<String> TYPES = Set.of(
            "STRING", "LONG", "DECIMAL", "BOOLEAN", "DATE", "DATETIME");

    public void validate(List<FieldMapping> mappings) {
        Set<String> targets = new java.util.HashSet<>();
        for (FieldMapping mapping : mappings) {
            identifier(mapping.sourceField(), "源字段");
            identifier(mapping.targetField(), "ClickHouse 目标字段");
            String type = mapping.targetType().toUpperCase(Locale.ROOT);
            if (!TYPES.contains(type)) {
                throw new BadRequestException("不支持的目标字段类型: " + mapping.targetType());
            }
            if (!targets.add(mapping.targetField())) {
                throw new BadRequestException("目标字段不能重复映射: " + mapping.targetField());
            }
            if (("DATE".equals(type) || "DATETIME".equals(type))
                    && (mapping.datePattern() == null || mapping.datePattern().isBlank())) {
                throw new BadRequestException("日期字段必须确认日期格式: " + mapping.sourceField());
            }
        }
    }

    public MappingResult map(SyncJobEntity job, Map<String, Object> source) {
        return map(job.mappings(), job.primaryKeyField(), job.updatedAtField(), source);
    }

    public MappingResult map(List<FieldMapping> mappings, String primaryKeyField,
            String updatedAtField, Map<String, ?> source) {
        validate(mappings);
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldMapping mapping : mappings) {
            Object raw = source.get(mapping.sourceField());
            if (mapping.required() && (raw == null || raw.toString().isBlank())) {
                throw new BadRequestException(mapping.sourceField() + " 为必填字段");
            }
            values.put(mapping.targetField(), convert(raw, mapping));
        }
        Object primaryKey = source.get(primaryKeyField);
        if (primaryKey == null || primaryKey.toString().isBlank()) {
            throw new BadRequestException(primaryKeyField + " 缺少稳定主键");
        }
        Instant updatedAt = updatedAtField == null || updatedAtField.isBlank()
                ? Instant.EPOCH : instant(source.get(updatedAtField), null, false);
        String sourceVersion = updatedAt.toEpochMilli() + ":" + primaryKey;
        Object deletedValue = source.get("is_deleted");
        boolean deleted = deletedValue != null && Set.of("1", "true", "是")
                .contains(deletedValue.toString().toLowerCase(Locale.ROOT));
        return new MappingResult(values, primaryKey.toString(), updatedAt, sourceVersion, deleted);
    }

    public Object convert(Object raw, FieldMapping mapping) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        String value = raw.toString().trim();
        try {
            return switch (mapping.targetType().toUpperCase(Locale.ROOT)) {
                case "STRING" -> value;
                case "LONG" -> Long.parseLong(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> booleanValue(value);
                case "DATE" -> LocalDate.parse(value, DateTimeFormatter.ofPattern(mapping.datePattern()));
                case "DATETIME" -> instant(value, mapping.datePattern(), true);
                default -> throw new BadRequestException("不支持的映射类型");
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new BadRequestException(mapping.sourceField() + " 无法转换为 " + mapping.targetType());
        }
    }

    public void identifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new BadRequestException(label + "不是受控标识符: " + value);
        }
    }

    private boolean booleanValue(String value) {
        if (Set.of("true", "1", "是").contains(value.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (Set.of("false", "0", "否").contains(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        throw new BadRequestException("布尔值必须为 true/false、1/0 或是/否");
    }

    private Instant instant(Object raw, String pattern, boolean patternRequired) {
        if (raw == null || raw.toString().isBlank()) {
            return Instant.EPOCH;
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (raw instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (raw instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        String value = raw.toString();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            if (patternRequired && (pattern == null || pattern.isBlank())) {
                throw ignored;
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    pattern == null || pattern.isBlank() ? "yyyy-MM-dd HH:mm:ss" : pattern);
            return LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC);
        }
    }
}
