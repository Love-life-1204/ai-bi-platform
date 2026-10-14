CREATE TABLE `sys_commercial_lock` (
  `lock_key` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='商业化事务互斥键';

CREATE TABLE `sys_commercial_state` (
  `namespace_code` VARCHAR(64) NOT NULL,
  `state_key` VARCHAR(255) NOT NULL,
  `value_type` VARCHAR(255) NOT NULL,
  `payload_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`namespace_code`, `state_key`),
  KEY `idx_sys_commercial_state_updated` (`namespace_code`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='商业化运行时持久状态';
