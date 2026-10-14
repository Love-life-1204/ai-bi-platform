package com.aibi.platform.query.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.query.dto.QueryDtos.Column;
import com.aibi.platform.query.dto.QueryDtos.Filter;
import com.aibi.platform.query.model.AuthorizedQuery;
import com.aibi.platform.query.model.QueryModels.BoundParameter;
import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Compiles the closed query AST to one parameterized ClickHouse SELECT. */
@Component
public class QueryPlanCompiler {

    private final PhysicalMappingManager physical;
    private final SemanticMapper semanticMapper;

    public QueryPlanCompiler(PhysicalMappingManager physical, SemanticMapper semanticMapper) {
        this.physical = physical;
        this.semanticMapper = semanticMapper;
    }

    public CompiledQuery compile(AuthorizedQuery query) {
        Context context = new Context(query);
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selections = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (Long fieldId : query.plan().dimensionFieldIds()) {
            DatasetField field = context.fieldsById.get(fieldId);
            String alias = "dimension_" + fieldId;
            String expression = context.field(field);
            if ("MASKED".equals(context.columnModes.get(fieldId))) {
                expression = "if(isNull(" + expression + "), NULL, concat(substring(toString("
                        + expression + "), 1, 1), '***', substring(toString("
                        + expression + "), -1, 1)))";
            }
            selections.add(expression + " AS `" + alias + "`");
            groups.add("`" + alias + "`");
            context.columns.add(new Column(alias, field.businessName(), field.dataType(), "DIMENSION"));
        }
        for (int index = 0; index < query.metricVersions().size(); index++) {
            MetricVersion version = query.metricVersions().get(index);
            Metric metric = query.metrics().get(index);
            String alias = "metric_" + version.id();
            selections.add(context.formula(version, new HashSet<>()) + " AS `" + alias + "`");
            context.columns.add(new Column(alias, metric.name(), "DECIMAL", "METRIC"));
        }
        sql.append(String.join(", ", selections));
        sql.append(" FROM (").append(versionedFactScan(context)).append(") AS facts WHERE `__deleted` = 0");
        appendPolicies(sql, context);
        appendCandidateFilters(sql, context);
        if (!groups.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groups));
        }
        if (!query.plan().sort().isEmpty()) {
            List<String> order = query.plan().sort().stream().map(value -> {
                String prefix = "FIELD".equals(value.targetType()) ? "dimension_" : "metric_";
                return "`" + prefix + value.targetId() + "` " + value.direction();
            }).toList();
            sql.append(" ORDER BY ").append(String.join(", ", order));
        }
        sql.append(" LIMIT ?");
        context.bind(query.plan().limit(), Types.INTEGER);
        String statement = sql.toString();
        rejectUnsafeStatement(statement);
        return new CompiledQuery(statement, List.copyOf(context.parameters),
                List.copyOf(context.columns), sha256(statement));
    }

    private String versionedFactScan(Context context) {
        List<String> projections = new ArrayList<>();
        projections.add("source_id");
        projections.add("business_key");
        projections.add("argMax(is_deleted, tuple(source_version, publish_seq, row_event_key)) AS `__deleted`");
        for (DatasetField field : context.query.datasetVersion().fields().stream()
                .filter(value -> context.requiredFieldIds.contains(value.id())).toList()) {
            projections.add("argMax(" + physical.column(field)
                    + ", tuple(source_version, publish_seq, row_event_key)) AS " + physical.alias(field));
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", projections))
                .append(" FROM ").append(physical.table(context.query.dataset()))
                .append(" WHERE tenant_id = ? AND dataset_id = ? AND generation_id = ?")
                .append(" AND publish_seq <= ? GROUP BY source_id, business_key");
        context.bind(context.query.tenantId(), Types.BIGINT);
        context.bind(context.query.dataset().id(), Types.BIGINT);
        context.bind(context.query.publication().activeGeneration(), Types.BIGINT);
        context.bind(context.query.publication().committedPublishSeq(), Types.BIGINT);
        return sql.toString();
    }

    private void appendPolicies(StringBuilder sql, Context context) {
        for (List<JsonNode> group : context.query.policy().rowPolicyGroups().values()) {
            sql.append(" AND (");
            List<String> alternatives = new ArrayList<>();
            for (JsonNode policy : group) {
                alternatives.add(policy(policy, context));
            }
            sql.append(String.join(" OR ", alternatives)).append(')');
        }
    }

    private String policy(JsonNode node, Context context) {
        String op = node.path("op").asText("").toUpperCase();
        return switch (op) {
            case "TRUE" -> "1 = 1";
            case "FALSE" -> "1 = 0";
            case "AND", "OR" -> compoundPolicy(node, context, op);
            case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> comparisonPolicy(node, context, op);
            case "IN" -> inPolicy(node, context);
            default -> throw new BadRequestException("行级策略包含不支持的操作符");
        };
    }

    private String compoundPolicy(JsonNode node, Context context, String op) {
        if (!node.path("args").isArray() || node.path("args").isEmpty()) {
            throw new BadRequestException("行级策略逻辑节点不能为空");
        }
        List<String> children = new ArrayList<>();
        node.path("args").forEach(value -> children.add(policy(value, context)));
        return "(" + String.join(" " + op + " ", children) + ")";
    }

    private String comparisonPolicy(JsonNode node, Context context, String op) {
        DatasetField field = context.fieldByKey(node.path("field").asText());
        JsonNode value = node.get("value");
        if (value == null || value.isContainerNode()) {
            throw new BadRequestException("行级策略比较值无效");
        }
        context.bind(scalar(value), jdbcType(field));
        return context.field(field) + " " + operator(op) + " ?";
    }

    private String inPolicy(JsonNode node, Context context) {
        DatasetField field = context.fieldByKey(node.path("field").asText());
        JsonNode values = node.path("values");
        if (!values.isArray() || values.isEmpty() || values.size() > 100) {
            throw new BadRequestException("行级策略 IN 值数量不合法");
        }
        List<String> placeholders = new ArrayList<>();
        values.forEach(value -> {
            context.bind(scalar(value), jdbcType(field));
            placeholders.add("?");
        });
        return context.field(field) + " IN (" + String.join(",", placeholders) + ")";
    }

    private void appendCandidateFilters(StringBuilder sql, Context context) {
        if (context.query.plan().timeRange() != null) {
            DatasetField field = context.fieldsById.get(context.query.plan().timeRange().fieldId());
            sql.append(" AND ").append(context.field(field)).append(" >= ? AND ")
                    .append(context.field(field)).append(" < ?");
            context.bind(java.sql.Timestamp.from(context.query.plan().timeRange().start().toInstant()),
                    Types.TIMESTAMP);
            context.bind(java.sql.Timestamp.from(context.query.plan().timeRange().end().toInstant()),
                    Types.TIMESTAMP);
        }
        for (Filter filter : context.query.plan().filters()) {
            DatasetField field = context.fieldsById.get(filter.fieldId());
            String expression = context.field(field);
            switch (filter.operator()) {
                case "IS_NULL" -> sql.append(" AND isNull(").append(expression).append(')');
                case "IS_NOT_NULL" -> sql.append(" AND isNotNull(").append(expression).append(')');
                case "IN", "NOT_IN" -> appendInFilter(sql, context, filter, field, expression);
                default -> {
                    if (filter.value() == null || filter.value().isContainerNode()) {
                        throw new BadRequestException("筛选值类型无效");
                    }
                    sql.append(" AND ").append(expression).append(' ')
                            .append(operator(filter.operator())).append(" ?");
                    context.bind(scalar(filter.value()), jdbcType(field));
                }
            }
        }
    }

    private void appendInFilter(StringBuilder sql, Context context, Filter filter,
            DatasetField field, String expression) {
        if (filter.value() == null || !filter.value().isArray()
                || filter.value().isEmpty() || filter.value().size() > 100) {
            throw new BadRequestException("IN 筛选必须包含 1 到 100 个值");
        }
        List<String> placeholders = new ArrayList<>();
        filter.value().forEach(value -> {
            context.bind(scalar(value), jdbcType(field));
            placeholders.add("?");
        });
        sql.append(" AND ").append(expression)
                .append("NOT_IN".equals(filter.operator()) ? " NOT IN (" : " IN (")
                .append(String.join(",", placeholders)).append(')');
    }

    private String operator(String op) {
        return switch (op) {
            case "EQ" -> "=";
            case "NE" -> "!=";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            default -> throw new BadRequestException("不支持的比较操作符");
        };
    }

    private Object scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private int jdbcType(DatasetField field) {
        return switch (field.dataType().toUpperCase()) {
            case "INTEGER", "LONG" -> Types.BIGINT;
            case "DECIMAL" -> Types.DECIMAL;
            case "BOOLEAN" -> Types.BOOLEAN;
            case "DATE" -> Types.DATE;
            case "DATETIME" -> Types.TIMESTAMP;
            default -> Types.VARCHAR;
        };
    }

    private void rejectUnsafeStatement(String sql) {
        String normalized = sql.toUpperCase();
        if (!normalized.startsWith("SELECT ") || normalized.contains(";")
                || normalized.contains(" SYSTEM.") || normalized.contains("INFORMATION_SCHEMA")
                || normalized.contains(" URL(") || normalized.contains(" FILE(")
                || normalized.contains(" REMOTE(")) {
            throw new BadRequestException("查询编译结果不符合只读安全策略");
        }
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private final class Context {
        private final AuthorizedQuery query;
        private final Map<Long, DatasetField> fieldsById = new HashMap<>();
        private final Map<String, DatasetField> fieldsByKey = new HashMap<>();
        private final Map<Long, String> columnModes = new HashMap<>();
        private final Set<Long> requiredFieldIds = new HashSet<>();
        private final List<BoundParameter> parameters = new ArrayList<>();
        private final List<Column> columns = new ArrayList<>();

        private Context(AuthorizedQuery query) {
            this.query = query;
            query.datasetVersion().fields().forEach(value -> {
                fieldsById.put(value.id(), value);
                fieldsByKey.put(value.stableFieldKey(), value);
            });
            query.policy().columns().forEach(value -> columnModes.put(
                    value.datasetFieldId(), value.accessMode()));
            requiredFieldIds.addAll(query.plan().dimensionFieldIds());
            query.plan().filters().forEach(value -> requiredFieldIds.add(value.fieldId()));
            if (query.plan().timeRange() != null) {
                requiredFieldIds.add(query.plan().timeRange().fieldId());
            }
            query.metricVersions().forEach(value -> collectRequiredFormula(
                    value.formulaAst(), new HashSet<>()));
            query.policy().rowPolicyGroups().values().forEach(group ->
                    group.forEach(this::collectPolicyFields));
        }

        private void collectRequiredFormula(JsonNode node, Set<Long> visiting) {
            if (node == null || !node.isObject()) {
                return;
            }
            if ("FIELD".equalsIgnoreCase(node.path("op").asText())) {
                requiredFieldIds.add(fieldByKey(node.path("field").asText()).id());
            }
            if ("METRIC".equalsIgnoreCase(node.path("op").asText())) {
                long id = node.path("metricVersionId").asLong(0);
                if (!visiting.add(id)) {
                    throw new BadRequestException("指标依赖存在循环");
                }
                MetricVersion dependency = semanticMapper.findMetricVersion(query.tenantId(), id)
                        .filter(value -> "PUBLISHED".equals(value.status())
                                && value.datasetVersionId() == query.datasetVersion().id())
                        .orElseThrow(() -> new BadRequestException("指标依赖版本无效"));
                collectRequiredFormula(dependency.formulaAst(), visiting);
                visiting.remove(id);
            }
            node.path("args").forEach(child -> collectRequiredFormula(child, visiting));
        }

        private void collectPolicyFields(JsonNode node) {
            if (node == null || !node.isObject()) {
                return;
            }
            if (node.hasNonNull("field")) {
                requiredFieldIds.add(fieldByKey(node.path("field").asText()).id());
            }
            node.path("args").forEach(this::collectPolicyFields);
        }

        private String field(DatasetField field) {
            return "facts." + physical.alias(field);
        }

        private DatasetField fieldByKey(String key) {
            DatasetField field = fieldsByKey.get(key);
            if (field == null) {
                throw new BadRequestException("策略引用了未知字段");
            }
            return field;
        }

        private void bind(Object value, int type) {
            parameters.add(new BoundParameter(value, type));
        }

        private String formula(MetricVersion metric, Set<Long> visiting) {
            if (!visiting.add(metric.id())) {
                throw new BadRequestException("指标依赖存在循环");
            }
            String result = formulaNode(metric.formulaAst(), visiting);
            visiting.remove(metric.id());
            return result;
        }

        private String formulaNode(JsonNode node, Set<Long> visiting) {
            String op = node.path("op").asText("").toUpperCase();
            JsonNode args = node.path("args");
            return switch (op) {
                case "FIELD" -> field(fieldByKey(node.path("field").asText()));
                case "LITERAL" -> literal(node.get("value"));
                case "SUM", "COUNT_DISTINCT", "MIN", "MAX", "AVG" ->
                        aggregate(op, args, visiting);
                case "COUNT" -> "count()";
                case "ADD", "SUB", "DIV", "EQ", "NE", "GT", "GTE", "LT", "LTE" ->
                        binary(op, args, visiting);
                case "NULLIF" -> function("nullIf", args, visiting, 2);
                case "CASE" -> function("if", args, visiting, 3);
                case "DATE_TRUNC" -> dateTrunc(node, visiting);
                case "METRIC" -> referencedMetric(node, visiting);
                default -> throw new BadRequestException("指标公式包含未支持的操作符: " + op);
            };
        }

        private String literal(JsonNode value) {
            if (value == null || value.isContainerNode()) {
                throw new BadRequestException("指标字面量无效");
            }
            bind(scalar(value), value.isNumber() ? Types.DECIMAL : Types.VARCHAR);
            return "?";
        }

        private String aggregate(String op, JsonNode args, Set<Long> visiting) {
            String function = "COUNT_DISTINCT".equals(op) ? "uniqExact" : op.toLowerCase();
            return function + "(" + argument(args, 0, 1, visiting) + ")";
        }

        private String binary(String op, JsonNode args, Set<Long> visiting) {
            String left = argument(args, 0, 2, visiting);
            String right = argument(args, 1, 2, visiting);
            String token = switch (op) {
                case "ADD" -> "+";
                case "SUB" -> "-";
                case "DIV" -> "/";
                default -> operator(op);
            };
            if ("DIV".equals(op)) {
                return "(" + left + " / nullIf(" + right + ", 0))";
            }
            return "(" + left + " " + token + " " + right + ")";
        }

        private String function(String name, JsonNode args, Set<Long> visiting, int count) {
            List<String> values = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                values.add(argument(args, index, count, visiting));
            }
            return name + "(" + String.join(",", values) + ")";
        }

        private String dateTrunc(JsonNode node, Set<Long> visiting) {
            String unit = node.path("unit").asText("").toLowerCase();
            if (!Set.of("day", "week", "month", "quarter", "year").contains(unit)) {
                throw new BadRequestException("DATE_TRUNC 粒度不受支持");
            }
            bind(unit, Types.VARCHAR);
            return "dateTrunc(?, " + argument(node.path("args"), 0, 1, visiting) + ")";
        }

        private String referencedMetric(JsonNode node, Set<Long> visiting) {
            long id = node.path("metricVersionId").asLong(0);
            MetricVersion dependency = semanticMapper.findMetricVersion(query.tenantId(), id)
                    .filter(value -> "PUBLISHED".equals(value.status())
                            && value.datasetVersionId() == query.datasetVersion().id())
                    .orElseThrow(() -> new BadRequestException("指标依赖版本无效"));
            return formula(dependency, visiting);
        }

        private String argument(JsonNode args, int index, int count, Set<Long> visiting) {
            if (!args.isArray() || args.size() != count) {
                throw new BadRequestException("指标公式参数数量无效");
            }
            return formulaNode(args.get(index), visiting);
        }
    }
}
