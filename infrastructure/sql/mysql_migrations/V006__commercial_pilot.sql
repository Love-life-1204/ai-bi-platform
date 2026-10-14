CREATE TABLE `saas_refund` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `provider_refund_id` VARCHAR(128) NOT NULL,
  `amount_minor_units` BIGINT UNSIGNED NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `refund_status` VARCHAR(32) NOT NULL,
  `reason_summary` VARCHAR(255) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_refund_provider` (`provider_code`, `provider_refund_id`),
  KEY `idx_saas_refund_order` (`tenant_id`, `order_id`, `created_at`),
  CONSTRAINT `fk_saas_refund_order` FOREIGN KEY (`tenant_id`, `order_id`)
    REFERENCES `saas_order` (`tenant_id`, `id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_saas_refund_amount` CHECK (`amount_minor_units` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='支付退款记录';

CREATE TABLE `sys_mfa_factor` (
  `id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `factor_type` VARCHAR(32) NOT NULL,
  `secret_ciphertext` VARBINARY(1024) NOT NULL,
  `factor_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `confirmed_at` DATETIME(3) NULL,
  `last_used_step` BIGINT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_mfa_user_type` (`user_id`, `factor_type`),
  CONSTRAINT `fk_sys_mfa_user` FOREIGN KEY (`user_id`)
    REFERENCES `sys_user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='企业用户多因素认证';

CREATE TABLE `sys_oidc_provider` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `issuer` VARCHAR(512) NOT NULL,
  `client_id` VARCHAR(255) NOT NULL,
  `client_secret_ref` VARCHAR(255) NULL,
  `authorization_endpoint` VARCHAR(512) NOT NULL,
  `token_endpoint` VARCHAR(512) NOT NULL,
  `jwks_uri` VARCHAR(512) NOT NULL,
  `email_domain` VARCHAR(255) NULL,
  `provider_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_oidc_tenant` (`tenant_id`),
  CONSTRAINT `fk_sys_oidc_tenant` FOREIGN KEY (`tenant_id`)
    REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户 OIDC 身份提供方';

CREATE TABLE `ai_usage_reconciliation` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `period_start` DATETIME(3) NOT NULL,
  `period_end` DATETIME(3) NOT NULL,
  `estimated_minor_units` BIGINT NOT NULL,
  `provider_minor_units` BIGINT NOT NULL,
  `difference_minor_units` BIGINT NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `reconciliation_status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_reconciliation`
    (`tenant_id`, `provider_code`, `period_start`, `period_end`),
  CONSTRAINT `fk_ai_usage_reconciliation_tenant` FOREIGN KEY (`tenant_id`)
    REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_usage_period` CHECK (`period_end` > `period_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI 供应商费用对账';

CREATE TABLE `sys_backup_drill` (
  `id` BIGINT UNSIGNED NOT NULL,
  `environment_code` VARCHAR(64) NOT NULL,
  `backup_reference` VARCHAR(512) NOT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `restored_at` DATETIME(3) NULL,
  `verified_at` DATETIME(3) NULL,
  `rpo_seconds` BIGINT UNSIGNED NULL,
  `rto_seconds` BIGINT UNSIGNED NULL,
  `drill_status` VARCHAR(32) NOT NULL,
  `evidence_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_sys_backup_drill` (`environment_code`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='备份恢复演练证据';
