-- AI BI clickhouse 最终初始化基线。请通过 scripts/db.py 执行。
-- 以下校验和仅用于识别已执行的旧版历史，不是待执行迁移。
-- legacy 001_create_databases.sql ac689f1d6eb9cd8d776cf4c0abd60acd80f6684ddd1478e8b2dd56b9b70bff40
-- legacy 002_raw_and_dwd.sql 6c19957d78dc77a8be2f9ffcc39343acac5dacc92103a95483ab99d4dd47e711
-- legacy 003_dws_and_ops.sql 6f52d96ef60989ba939d4dea3555ed0144cc042b25681e0b71d5d755c993abca

CREATE DATABASE IF NOT EXISTS ai_bi_raw;

CREATE DATABASE IF NOT EXISTS ai_bi_dwd;

CREATE DATABASE IF NOT EXISTS ai_bi_dws;

CREATE DATABASE IF NOT EXISTS ai_bi_ops;

CREATE TABLE IF NOT EXISTS ai_bi_raw.ingest_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, generation_id UInt64, publish_seq UInt64,
    batch_id UInt64, row_event_key String, source_version UInt64,
    is_deleted UInt8, row_hash FixedString(64), payload_json String,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ingested_at)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, business_key, source_version, publish_seq, row_event_key)
TTL toDateTime(ingested_at) + INTERVAL 30 DAY DELETE;

CREATE TABLE IF NOT EXISTS ai_bi_dwd.sales_order_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, order_id String,
    immutable_partition_date Date, business_time DateTime64(3, 'UTC'),
    generation_id UInt64, publish_seq UInt64, batch_id UInt64,
    source_version UInt64, row_event_key String, row_hash FixedString(64),
    is_deleted UInt8, region_id String, department_id String,
    customer_id String, order_status LowCardinality(String),
    currency FixedString(3), paid_minor_units Int64, refunded_minor_units Int64,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(immutable_partition_date)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, order_id, source_version, publish_seq, row_event_key);

CREATE TABLE IF NOT EXISTS ai_bi_dwd.sales_order_item_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, order_id String, line_id String, product_id String,
    immutable_partition_date Date, business_time DateTime64(3, 'UTC'),
    generation_id UInt64, publish_seq UInt64, batch_id UInt64,
    source_version UInt64, row_event_key String, row_hash FixedString(64),
    is_deleted UInt8, region_id String, department_id String,
    currency FixedString(3), quantity Decimal(20,6),
    paid_minor_units Int64, refunded_minor_units Int64, cost_minor_units Int64,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(immutable_partition_date)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, order_id, line_id, source_version, publish_seq, row_event_key);

CREATE TABLE IF NOT EXISTS ai_bi_dwd.inventory_snapshot_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, warehouse_id String, product_id String,
    immutable_partition_date Date, business_time DateTime64(3, 'UTC'),
    generation_id UInt64, publish_seq UInt64, batch_id UInt64,
    source_version UInt64, row_event_key String, row_hash FixedString(64),
    is_deleted UInt8, on_hand_quantity Decimal(20,6), reserved_quantity Decimal(20,6),
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(immutable_partition_date)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, business_key, source_version, publish_seq, row_event_key);

CREATE TABLE IF NOT EXISTS ai_bi_dwd.product_scd2_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, product_id String, product_name String, category_id String,
    valid_from DateTime64(3, 'UTC'), valid_to DateTime64(3, 'UTC'),
    generation_id UInt64, publish_seq UInt64, batch_id UInt64,
    source_version UInt64, row_event_key String, row_hash FixedString(64),
    is_deleted UInt8, attributes_json String,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(valid_from)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, business_key, source_version, publish_seq, row_event_key);

CREATE TABLE IF NOT EXISTS ai_bi_dwd.customer_scd2_event
(
    tenant_id UInt64, dataset_id UInt64, source_id UInt64,
    business_key String, customer_id String, segment_id String, region_id String,
    valid_from DateTime64(3, 'UTC'), valid_to DateTime64(3, 'UTC'),
    generation_id UInt64, publish_seq UInt64, batch_id UInt64,
    source_version UInt64, row_event_key String, row_hash FixedString(64),
    is_deleted UInt8, attributes_json String,
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(valid_from)
ORDER BY (tenant_id, dataset_id, generation_id, source_id, business_key, source_version, publish_seq, row_event_key);

CREATE TABLE IF NOT EXISTS ai_bi_dws.sales_daily_snapshot
(
    tenant_id UInt64, dataset_id UInt64, metric_version_id UInt64,
    snapshot_id String, generation_id UInt64, committed_publish_seq UInt64,
    business_date Date, timezone String, region_id String, currency FixedString(3),
    net_sales_minor_units Int64, order_count UInt64,
    snapshot_row_key String, row_hash FixedString(64),
    computed_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(business_date)
ORDER BY (tenant_id, dataset_id, metric_version_id, snapshot_id, business_date, region_id, currency, snapshot_row_key);

CREATE TABLE IF NOT EXISTS ai_bi_ops.batch_ingest_receipt
(
    tenant_id UInt64, dataset_id UInt64, generation_id UInt64, publish_seq UInt64,
    batch_id UInt64, target_table String, replica_name String,
    receipt_version UInt64, expected_rows UInt64, actual_distinct_rows UInt64,
    expected_hash FixedString(64), actual_hash FixedString(64),
    quality_status LowCardinality(String), checked_at DateTime64(3, 'UTC'),
    fencing_token UInt64
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(checked_at)
ORDER BY (tenant_id, dataset_id, generation_id, publish_seq, batch_id, target_table, replica_name, receipt_version);
