package com.aibi.platform.query.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Resolves semantic names to identifiers from trusted published metadata only. */
@Component
public class PhysicalMappingManager {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final Map<String, String> STANDARD_TABLES = Map.of(
            "orders", "sales_order_event",
            "sales_orders", "sales_order_event",
            "sales_order", "sales_order_event",
            "order_items", "sales_order_item_event",
            "sales_order_items", "sales_order_item_event",
            "inventory", "inventory_snapshot_event",
            "inventory_snapshot", "inventory_snapshot_event",
            "products", "product_scd2_event",
            "customers", "customer_scd2_event");
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "tenant_id", "dataset_id", "source_id", "business_key", "generation_id",
            "publish_seq", "source_version", "row_event_key", "is_deleted");

    public String table(Dataset dataset) {
        String name = STANDARD_TABLES.getOrDefault(dataset.code(), dataset.code() + "_event");
        identifier(name, "数据集物理表");
        if (name.toLowerCase().startsWith("system") || name.toLowerCase().startsWith("information_schema")) {
            throw new BadRequestException("禁止访问系统表");
        }
        return "ai_bi_dwd." + quote(name);
    }

    public String column(DatasetField field) {
        identifier(field.physicalName(), "数据字段物理列");
        if (SYSTEM_COLUMNS.contains(field.physicalName().toLowerCase())) {
            throw new BadRequestException("业务字段不能映射查询控制列: " + field.stableFieldKey());
        }
        return quote(field.physicalName());
    }

    public String alias(DatasetField field) {
        return quote("field_" + field.id());
    }

    private void identifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new BadRequestException(label + "不是受控标识符");
        }
    }

    private String quote(String value) {
        return "`" + value + "`";
    }
}
