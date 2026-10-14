package com.aibi.platform.semantic.manager;

import com.aibi.platform.semantic.dto.SemanticDtos.FieldInput;
import com.aibi.platform.semantic.dto.SemanticDtos.RelationInput;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationIssue;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetRelation;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class SemanticDefinitionManager {

    private static final Set<String> TYPES = Set.of(
            "STRING", "BOOLEAN", "INTEGER", "LONG", "DECIMAL", "DATE", "DATETIME", "TIMESTAMP");
    private static final Set<String> DEDUPLICATION_RULES = Set.of("FIRST", "LAST", "MAX_UPDATED_AT");
    private final SemanticMapper mapper;
    private final ObjectMapper objectMapper;

    public SemanticDefinitionManager(SemanticMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<DatasetField> fields(List<FieldInput> inputs) {
        return fields(inputs, List.of());
    }

    public List<DatasetField> fields(List<FieldInput> inputs, List<DatasetField> existing) {
        Map<String, Long> existingIds = new HashMap<>();
        existing.forEach(field -> existingIds.put(field.stableFieldKey(), field.id()));
        return inputs.stream().map(input -> new DatasetField(
                existingIds.getOrDefault(input.stableFieldKey().trim(), newId()),
                input.stableFieldKey().trim(), input.sourceColumnId(), input.physicalName().trim(),
                input.businessName().trim(), input.dataType().trim().toUpperCase(),
                input.role(), input.sensitivity(), blankToNullPreservingCase(input.displayFormat()))).toList();
    }

    public List<DatasetRelation> relations(List<RelationInput> inputs) {
        return relations(inputs, List.of());
    }

    public List<DatasetRelation> relations(List<RelationInput> inputs,
            List<DatasetRelation> existing) {
        if (inputs == null) {
            return List.of();
        }
        Map<String, Long> existingIds = new HashMap<>();
        existing.forEach(relation -> existingIds.put(relation.code(), relation.id()));
        return inputs.stream().map(input -> new DatasetRelation(
                existingIds.getOrDefault(input.code().trim(), newId()),
                input.code().trim(), input.relatedVersionId(), input.leftFieldKey().trim(),
                input.rightFieldKey().trim(), input.cardinality(), input.approved(),
                input.rightKeyUnique(), blankToNull(input.deduplicationRule()))).toList();
    }

    public ValidationResult validate(long tenantId, long workspaceId, DatasetVersion version) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < version.fields().size(); index++) {
            DatasetField field = version.fields().get(index);
            String path = "fields[" + index + "]";
            if (!keys.add(field.stableFieldKey())) {
                errors.add(issue("DUPLICATE_FIELD_KEY", path + ".stableFieldKey", "稳定字段键不能重复"));
            }
            if (!names.add(field.businessName())) {
                errors.add(issue("DUPLICATE_BUSINESS_NAME", path + ".businessName", "业务名称不能重复"));
            }
            if (!TYPES.contains(field.dataType())) {
                errors.add(issue("UNSUPPORTED_FIELD_TYPE", path + ".dataType", "不支持的业务字段类型"));
            }
            if (field.sourceColumnId() == null && "MEASURE".equals(field.role())) {
                warnings.add(issue("CALCULATED_MEASURE", path, "度量未绑定源字段，将按计算字段处理"));
            }
        }
        Set<String> relationCodes = new HashSet<>();
        for (int index = 0; index < version.relations().size(); index++) {
            DatasetRelation relation = version.relations().get(index);
            String path = "relations[" + index + "]";
            if (!relationCodes.add(relation.code())) {
                errors.add(issue("DUPLICATE_RELATION_CODE", path + ".code", "关联编码不能重复"));
            }
            if (!relation.approved()) {
                errors.add(issue("RELATION_NOT_APPROVED", path + ".approved", "数据集关联必须经过批准"));
            }
            DatasetVersion related = mapper.findDatasetVersion(tenantId, relation.relatedVersionId()).orElse(null);
            if (related == null || !"PUBLISHED".equals(related.status())) {
                errors.add(issue("RELATED_VERSION_NOT_PUBLISHED", path + ".relatedVersionId",
                        "关联目标必须是同租户的已发布数据集版本"));
            } else {
                mapper.findDataset(tenantId, related.datasetId()).filter(dataset -> dataset.workspaceId() == workspaceId)
                        .orElseGet(() -> {
                            errors.add(issue("RELATED_WORKSPACE_MISMATCH", path,
                                    "关联目标必须属于同一工作空间"));
                            return null;
                        });
            }
            if (!keys.contains(relation.leftFieldKey())) {
                errors.add(issue("LEFT_KEY_NOT_FOUND", path + ".leftFieldKey", "左关联字段不存在"));
            }
            boolean uniqueRequired = "ONE_TO_ONE".equals(relation.cardinality())
                    || "MANY_TO_ONE".equals(relation.cardinality());
            if (uniqueRequired && !relation.rightKeyUnique()
                    && (relation.deduplicationRule() == null
                            || !DEDUPLICATION_RULES.contains(relation.deduplicationRule()))) {
                errors.add(issue("DUPLICATE_DIMENSION_KEY", path + ".rightFieldKey",
                        "关联基数要求右侧维度键唯一，或配置批准的去重规则"));
            }
            if ("MANY_TO_MANY".equals(relation.cardinality())) {
                errors.add(issue("MANY_TO_MANY_NOT_ALLOWED", path + ".cardinality",
                        "多对多关联会重复累计度量，不允许发布"));
            }
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    public String hash(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("语义定义无法计算哈希", exception);
        }
    }

    private ValidationIssue issue(String code, String path, String message) {
        return new ValidationIssue(code, path, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String blankToNullPreservingCase(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
