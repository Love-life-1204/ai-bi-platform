ALTER TABLE `bi_import_job`
  ADD COLUMN `strict_mode` TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER `mapping_json`,
  ADD COLUMN `valid_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `total_rows`,
  ADD COLUMN `error_summary` VARCHAR(500) NULL AFTER `error_object_id`;

ALTER TABLE `bi_sync_run`
  ADD COLUMN `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `error_summary`,
  ADD COLUMN `dead_letter` TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER `retry_count`;

ALTER TABLE `bi_batch`
  ADD COLUMN `received_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `actual_rows`,
  ADD COLUMN `valid_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `received_rows`,
  ADD COLUMN `rejected_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `valid_rows`,
  ADD COLUMN `deduplicated_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `rejected_rows`,
  ADD COLUMN `source_amount` DECIMAL(38,6) NOT NULL DEFAULT 0 AFTER `deduplicated_rows`,
  ADD COLUMN `target_amount` DECIMAL(38,6) NOT NULL DEFAULT 0 AFTER `source_amount`,
  ADD COLUMN `source_business_keys` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `target_amount`,
  ADD COLUMN `target_business_keys` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `source_business_keys`,
  ADD COLUMN `error_report` VARCHAR(500) NULL AFTER `quality_status`;

ALTER TABLE `bi_quality_result`
  DROP FOREIGN KEY `fk_135`,
  MODIFY COLUMN `quality_rule_id` BIGINT UNSIGNED NULL,
  ADD COLUMN `system_rule_code` VARCHAR(64) NULL AFTER `quality_rule_id`;

ALTER TABLE `bi_quality_result`
  ADD CONSTRAINT `fk_135` FOREIGN KEY (`tenant_id`, `quality_rule_id`)
    REFERENCES `bi_quality_rule` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE `bi_sync_event_dedup` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `source_id` BIGINT UNSIGNED NOT NULL,
  `generation_id` BIGINT UNSIGNED NOT NULL,
  `publish_seq` BIGINT UNSIGNED NOT NULL,
  `business_key_hash` CHAR(64) NOT NULL,
  `source_version_hash` CHAR(64) NOT NULL,
  `batch_id` BIGINT UNSIGNED NOT NULL,
  `fencing_token` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event` (`tenant_id`, `dataset_id`, `source_id`, `generation_id`, `publish_seq`,
    `business_key_hash`, `source_version_hash`),
  KEY `idx_batch` (`tenant_id`, `batch_id`),
  CONSTRAINT `fk_ingestion_001` FOREIGN KEY (`tenant_id`, `batch_id`)
    REFERENCES `bi_batch` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs
  COMMENT='同步事件持久化幂等收件箱';

CREATE TABLE `bi_sync_dead_letter` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `sync_run_id` BIGINT UNSIGNED NOT NULL,
  `error_code` VARCHAR(64) NOT NULL,
  `error_summary` VARCHAR(500) NOT NULL,
  `payload_json` JSON NULL,
  `dead_status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  `compensated_by` VARCHAR(128) NULL,
  `compensation_reason` VARCHAR(256) NULL,
  `compensated_run_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run` (`tenant_id`, `sync_run_id`),
  KEY `idx_open` (`tenant_id`, `dead_status`, `created_at`),
  CONSTRAINT `fk_ingestion_002` FOREIGN KEY (`tenant_id`, `sync_run_id`)
    REFERENCES `bi_sync_run` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_ingestion_003` FOREIGN KEY (`tenant_id`, `compensated_run_id`)
    REFERENCES `bi_sync_run` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs
  COMMENT='同步死信与人工补偿审计';

CREATE TABLE `bi_sync_staged_row` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `source_id` BIGINT UNSIGNED NOT NULL,
  `generation_id` BIGINT UNSIGNED NOT NULL,
  `publish_seq` BIGINT UNSIGNED NOT NULL,
  `business_key` VARCHAR(512) NOT NULL,
  `source_version` VARCHAR(512) NOT NULL,
  `batch_id` BIGINT UNSIGNED NOT NULL,
  `fencing_token` BIGINT UNSIGNED NOT NULL,
  `values_json` JSON NOT NULL,
  `amount` DECIMAL(38,6) NOT NULL DEFAULT 0,
  `source_updated_at` DATETIME(3) NOT NULL,
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `write_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`tenant_id`, `batch_id`),
  KEY `idx_visible` (`tenant_id`, `dataset_id`, `source_id`, `generation_id`, `publish_seq`),
  CONSTRAINT `fk_ingestion_004` FOREIGN KEY (`tenant_id`, `batch_id`)
    REFERENCES `bi_batch` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs
  COMMENT='待写入 ClickHouse 的持久化行与质量校验证据';

INSERT INTO `sys_permission`
  (`id`, `permission_code`, `permission_name`, `resource_type`, `action_code`) VALUES
  (21, 'import:read', '查看文件导入', 'IMPORT', 'read'),
  (22, 'import:manage', '管理文件导入', 'IMPORT', 'manage'),
  (23, 'sync:read', '查看数据同步', 'SYNC', 'read'),
  (24, 'sync:manage', '管理数据同步', 'SYNC', 'manage'),
  (25, 'quality:read', '查看数据质量', 'QUALITY', 'read'),
  (26, 'quality:manage', '管理数据质量', 'QUALITY', 'manage');
