ALTER TABLE `ai_conversation`
  ADD COLUMN `context_version` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `conversation_status`;

ALTER TABLE `ai_task`
  ADD COLUMN `attempt` INT UNSIGNED NOT NULL DEFAULT 1 AFTER `task_status`,
  ADD COLUMN `context_version` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `attempt`,
  ADD COLUMN `last_sequence` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `context_version`;

ALTER TABLE `ai_message`
  ADD COLUMN `ai_task_id` BIGINT UNSIGNED NULL AFTER `query_task_id`,
  ADD CONSTRAINT `fk_ai_message_ai_task` FOREIGN KEY (`tenant_id`, `ai_task_id`)
    REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE RESTRICT;

CREATE TABLE `ai_conversation_context` (
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `context_version` INT UNSIGNED NOT NULL,
  `context_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`tenant_id`, `conversation_id`, `context_version`),
  CONSTRAINT `fk_ai_context_conversation` FOREIGN KEY (`tenant_id`, `conversation_id`)
    REFERENCES `ai_conversation` (`tenant_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI会话确认上下文版本';

CREATE TABLE `ai_task_auth_snapshot` (
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ai_task_id` BIGINT UNSIGNED NOT NULL,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `tenant_permission_version` BIGINT UNSIGNED NOT NULL,
  `member_permission_version` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `asset_id` BIGINT UNSIGNED NULL,
  `action_code` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`tenant_id`, `ai_task_id`),
  CONSTRAINT `fk_ai_auth_task` FOREIGN KEY (`tenant_id`, `ai_task_id`)
    REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI任务授权快照';

CREATE TABLE `ai_task_event` (
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ai_task_id` BIGINT UNSIGNED NOT NULL,
  `sequence` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `trace_id` VARCHAR(128) NOT NULL,
  `payload_json` JSON NOT NULL,
  `emitted_at` DATETIME(3) NOT NULL,
  `retention_at` DATETIME(3) NOT NULL,
  PRIMARY KEY (`tenant_id`, `ai_task_id`, `sequence`),
  KEY `idx_ai_task_event_retention` (`retention_at`),
  CONSTRAINT `fk_ai_event_task` FOREIGN KEY (`tenant_id`, `ai_task_id`)
    REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='可恢复AI任务事件';

