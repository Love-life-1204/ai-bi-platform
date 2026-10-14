-- AI BI mysql 最终初始化基线。请通过 scripts/db.py 执行。
-- 以下校验和仅用于识别已执行的旧版历史，不是待执行迁移。
-- legacy V001 0777147bb40254f3732de7532029eda232bc4bbeb61c99d597175f702972099c
-- legacy V002 d3854296af627a527f81858e70a273af92fffe2d28bc592368681a14f296bf00
-- legacy V003 ef063bbbcaa225eb331a768012194e99b0b5474d8217987434764a94dfbd154a
-- legacy V004 85a6534c0843f8eb67792d82af4cff02c92c98e8d99d26dc7e5a50aa515c9ecd
-- legacy V005 6baff059b5acf3935a1d345d3fe6ee3f2ed4a47e7ac60f4546b1a5cf5a98ef45
-- legacy V006 323564c1bca51df25b7e738edd3a5cf1bd194f1483a6a605f0592689e30c8f3b
-- legacy V007 f8b8c8f4ad6050912bf70a80eb625ae5611385e61c71167d43e81665c42f45b3
-- legacy V008 a36cb83db17e50f92d99b2a735f7713d09ab3c878a5ed8c259de2a8804bffe7d

SET time_zone = '+00:00';

CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL,
  `email` VARCHAR(254) NOT NULL,
  `display_name` VARCHAR(200) NOT NULL,
  `password_hash` VARCHAR(255) NULL,
  `user_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='全局用户';

CREATE TABLE `sys_tenant` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_code` VARCHAR(64) NOT NULL,
  `tenant_name` VARCHAR(200) NOT NULL,
  `tenant_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `timezone` VARCHAR(128) NOT NULL DEFAULT 'Asia/Shanghai',
  `default_currency` CHAR(3) NOT NULL DEFAULT 'CNY',
  `permission_version` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='企业租户';

CREATE TABLE `sys_login_session` (
  `id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) NULL,
  `device_fingerprint` CHAR(64) NULL,
  `rotated_from_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`token_hash`),
  KEY `idx_1` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='登录会话';

CREATE TABLE `sys_tenant_member` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `member_name` VARCHAR(200) NOT NULL,
  `member_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `is_owner` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `permission_version` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `joined_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `user_id`),
  KEY `idx_1` (`tenant_id`, `member_status`, `created_at`),
  CONSTRAINT `ck_sys_tenant_member_1` CHECK (is_owner IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户成员';

CREATE TABLE `sys_department` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `parent_id` BIGINT UNSIGNED NULL,
  `department_code` VARCHAR(64) NOT NULL,
  `department_name` VARCHAR(200) NOT NULL,
  `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `department_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='部门';

CREATE TABLE `sys_member_department` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `department_id` BIGINT UNSIGNED NOT NULL,
  `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `member_id`, `department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='成员部门';

CREATE TABLE `sys_workspace` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_code` VARCHAR(64) NOT NULL,
  `workspace_name` VARCHAR(200) NOT NULL,
  `workspace_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='工作空间';

CREATE TABLE `sys_workspace_member` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `member_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='空间成员';

CREATE TABLE `sys_invitation` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `email` VARCHAR(254) NOT NULL,
  `invite_token_hash` CHAR(64) NOT NULL,
  `inviter_member_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NULL,
  `role_id` BIGINT UNSIGNED NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `accepted_at` DATETIME(3) NULL,
  `invitation_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `invite_token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='成员邀请';

CREATE TABLE `sys_tenant_setting` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `setting_key` VARCHAR(64) NOT NULL,
  `setting_value` JSON NOT NULL,
  `setting_version` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户配置';

CREATE TABLE `sys_tenant_resource` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_alias` VARCHAR(64) NOT NULL,
  `locator_json` JSON NOT NULL,
  `credential_ref` VARCHAR(255) NULL,
  `resource_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `resource_type`, `resource_alias`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户资源路由';

SET time_zone = '+00:00';

CREATE TABLE `sys_permission` (
  `id` BIGINT UNSIGNED NOT NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(200) NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='权限字典';

CREATE TABLE `sys_role` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(200) NOT NULL,
  `role_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `is_builtin` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户角色';

CREATE TABLE `sys_role_permission` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `role_id` BIGINT UNSIGNED NOT NULL,
  `permission_id` BIGINT UNSIGNED NOT NULL,
  `effect` VARCHAR(32) NOT NULL DEFAULT 'ALLOW',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `role_id`, `permission_id`),
  CONSTRAINT `ck_sys_role_permission_1` CHECK (effect IN ('ALLOW','DENY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色权限';

CREATE TABLE `sys_member_role` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `role_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NULL,
  `scope_key` VARCHAR(64) NOT NULL DEFAULT 'TENANT',
  `expires_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `member_id`, `role_id`, `scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='成员角色作用域';

CREATE TABLE `sys_asset` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `asset_type` VARCHAR(32) NOT NULL,
  `asset_ref_id` BIGINT UNSIGNED NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `asset_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `asset_type`, `asset_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='授权资产';

CREATE TABLE `sys_asset_acl` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `asset_id` BIGINT UNSIGNED NOT NULL,
  `principal_type` VARCHAR(32) NOT NULL,
  `principal_id` BIGINT UNSIGNED NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `effect` VARCHAR(32) NOT NULL,
  `expires_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `asset_id`, `principal_type`, `principal_id`, `action_code`),
  CONSTRAINT `ck_sys_asset_acl_1` CHECK (effect IN ('ALLOW','DENY')),
  CONSTRAINT `ck_sys_asset_acl_2` CHECK (principal_type IN ('MEMBER','ROLE','DEPARTMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='资产授权';

CREATE TABLE `sys_data_policy` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `policy_code` VARCHAR(64) NOT NULL,
  `policy_name` VARCHAR(200) NOT NULL,
  `policy_ast` JSON NOT NULL,
  `policy_group` VARCHAR(64) NOT NULL,
  `policy_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `policy_version` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `policy_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='行级策略';

CREATE TABLE `sys_policy_grant` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `policy_id` BIGINT UNSIGNED NOT NULL,
  `principal_type` VARCHAR(32) NOT NULL,
  `principal_id` BIGINT UNSIGNED NOT NULL,
  `expires_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `policy_id`, `principal_type`, `principal_id`),
  CONSTRAINT `ck_sys_policy_grant_1` CHECK (principal_type IN ('MEMBER','ROLE','DEPARTMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='行策略授权';

CREATE TABLE `sys_column_policy` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_field_id` BIGINT UNSIGNED NOT NULL,
  `principal_type` VARCHAR(32) NOT NULL,
  `principal_id` BIGINT UNSIGNED NOT NULL,
  `access_mode` VARCHAR(32) NOT NULL,
  `mask_config` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_field_id`, `principal_type`, `principal_id`),
  CONSTRAINT `ck_sys_column_policy_1` CHECK (access_mode IN ('VISIBLE','MASKED','DENY')),
  CONSTRAINT `ck_sys_column_policy_2` CHECK (principal_type IN ('MEMBER','ROLE','DEPARTMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='列权限';

CREATE TABLE `sys_identity_provider` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `provider_type` VARCHAR(32) NOT NULL,
  `issuer_hash` CHAR(64) NOT NULL,
  `issuer_url` VARCHAR(512) NOT NULL,
  `client_id` VARCHAR(128) NOT NULL,
  `credential_ref` VARCHAR(255) NOT NULL,
  `config_json` JSON NOT NULL,
  `provider_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `provider_type`, `issuer_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='企业身份源';

SET time_zone = '+00:00';

CREATE TABLE `bi_datasource` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `datasource_code` VARCHAR(64) NOT NULL,
  `datasource_name` VARCHAR(200) NOT NULL,
  `datasource_type` VARCHAR(32) NOT NULL,
  `endpoint_json` JSON NOT NULL,
  `credential_ref` VARCHAR(255) NULL,
  `owner_member_id` BIGINT UNSIGNED NULL,
  `last_test_status` VARCHAR(64) NULL,
  `last_test_at` DATETIME(3) NULL,
  `datasource_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `datasource_code`),
  KEY `idx_owner_member` (`tenant_id`, `owner_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='只读数据源';

CREATE TABLE `bi_source_object` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `datasource_id` BIGINT UNSIGNED NOT NULL,
  `schema_name` VARCHAR(128) NOT NULL,
  `object_name` VARCHAR(128) NOT NULL,
  `object_type` VARCHAR(32) NOT NULL,
  `schema_hash` CHAR(64) NOT NULL,
  `metadata_json` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `datasource_id`, `schema_name`, `object_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='源数据对象';

CREATE TABLE `bi_source_column` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `source_object_id` BIGINT UNSIGNED NOT NULL,
  `column_name` VARCHAR(128) NOT NULL,
  `stable_column_key` VARCHAR(64) NOT NULL,
  `ordinal_position` INT UNSIGNED NOT NULL,
  `data_type` VARCHAR(128) NOT NULL,
  `is_nullable` TINYINT UNSIGNED NOT NULL,
  `is_primary_key` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `sensitivity` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  `column_comment` TEXT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `source_object_id`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='源字段';

CREATE TABLE `bi_metadata_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `datasource_id` BIGINT UNSIGNED NOT NULL,
  `snapshot_version` BIGINT UNSIGNED NOT NULL,
  `schema_hash` CHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `diff_object_id` BIGINT UNSIGNED NULL,
  `collected_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `datasource_id`, `snapshot_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='元数据快照';

CREATE TABLE `bi_import_job` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `object_file_id` BIGINT UNSIGNED NOT NULL,
  `requester_member_id` BIGINT UNSIGNED NOT NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `mapping_json` JSON NULL,
  `import_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `total_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `error_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `error_object_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='文件导入任务';

CREATE TABLE `bi_sync_job` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `datasource_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `job_code` VARCHAR(64) NOT NULL,
  `sync_mode` VARCHAR(32) NOT NULL,
  `schedule_expression` VARCHAR(128) NULL,
  `timezone` VARCHAR(128) NOT NULL DEFAULT 'Asia/Shanghai',
  `config_json` JSON NOT NULL,
  `job_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='同步作业';

CREATE TABLE `bi_sync_watermark` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `sync_job_id` BIGINT UNSIGNED NOT NULL,
  `last_updated_at` DATETIME(3) NULL,
  `last_primary_key` VARCHAR(255) NULL,
  `watermark_json` JSON NULL,
  `last_batch_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `sync_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='发布水位';

CREATE TABLE `bi_sync_run` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `sync_job_id` BIGINT UNSIGNED NOT NULL,
  `attempt_no` INT UNSIGNED NOT NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `run_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `lease_owner` VARCHAR(128) NULL,
  `lease_until` DATETIME(3) NULL,
  `fencing_token` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `error_code` VARCHAR(64) NULL,
  `error_summary` TEXT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `request_key`),
  KEY `idx_1` (`tenant_id`, `run_status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='同步运行与租约';

CREATE TABLE `bi_batch` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `sync_run_id` BIGINT UNSIGNED NULL,
  `import_job_id` BIGINT UNSIGNED NULL,
  `batch_code` VARCHAR(128) NOT NULL,
  `generation_id` BIGINT UNSIGNED NOT NULL,
  `publish_seq` BIGINT UNSIGNED NOT NULL,
  `batch_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `expected_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `actual_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `content_hash` CHAR(64) NULL,
  `quality_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `fencing_token` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `ready_at` DATETIME(3) NULL,
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `batch_code`),
  UNIQUE KEY `uk_2` (`tenant_id`, `dataset_id`, `generation_id`, `publish_seq`),
  UNIQUE KEY `uk_3` (`tenant_id`, `dataset_id`, `generation_id`, `publish_seq`, `id`),
  KEY `idx_1` (`tenant_id`, `batch_status`, `created_at`),
  CONSTRAINT `ck_bi_batch_1` CHECK (publish_seq >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='发布批次';

CREATE TABLE `bi_quality_rule` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `rule_code` VARCHAR(64) NOT NULL,
  `rule_type` VARCHAR(32) NOT NULL,
  `rule_config` JSON NOT NULL,
  `severity` VARCHAR(32) NOT NULL DEFAULT 'BLOCK',
  `rule_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_id`, `rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='质量规则';

CREATE TABLE `bi_quality_result` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `batch_id` BIGINT UNSIGNED NOT NULL,
  `quality_rule_id` BIGINT UNSIGNED NOT NULL,
  `result_status` VARCHAR(32) NOT NULL,
  `observed_value` JSON NULL,
  `expected_value` JSON NULL,
  `evidence_object_id` BIGINT UNSIGNED NULL,
  `checked_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `batch_id`, `quality_rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='质量检查证据';

SET time_zone = '+00:00';

CREATE TABLE `bi_dataset` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `dataset_code` VARCHAR(64) NOT NULL,
  `dataset_name` VARCHAR(200) NOT NULL,
  `dataset_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `current_version_id` BIGINT UNSIGNED NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `dataset_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='稳定数据集';

CREATE TABLE `bi_dataset_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `source_object_id` BIGINT UNSIGNED NULL,
  `definition_json` JSON NOT NULL,
  `definition_hash` CHAR(64) NOT NULL,
  `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_id`, `version_no`),
  UNIQUE KEY `uk_2` (`tenant_id`, `dataset_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='数据集语义版本';

CREATE TABLE `bi_dataset_field` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `stable_field_key` VARCHAR(64) NOT NULL,
  `field_name` VARCHAR(200) NOT NULL,
  `data_type` VARCHAR(128) NOT NULL,
  `source_column_id` BIGINT UNSIGNED NULL,
  `expression_ast` JSON NULL,
  `sensitivity` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  `is_dimension` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_version_id`, `stable_field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='语义字段';

CREATE TABLE `bi_dataset_relation` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `related_version_id` BIGINT UNSIGNED NOT NULL,
  `relation_code` VARCHAR(64) NOT NULL,
  `cardinality` VARCHAR(32) NOT NULL,
  `join_ast` JSON NOT NULL,
  `effective_from` DATETIME(3) NULL,
  `effective_to` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_version_id`, `relation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='数据集关联';

CREATE TABLE `bi_dataset_publication` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_id` BIGINT UNSIGNED NOT NULL,
  `active_generation` BIGINT UNSIGNED NULL,
  `committed_publish_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `data_quality_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `last_batch_id` BIGINT UNSIGNED NULL,
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dataset_id`),
  CONSTRAINT `ck_bi_dataset_publication_1` CHECK ((active_generation IS NULL AND committed_publish_seq = 0) OR (active_generation IS NOT NULL AND committed_publish_seq >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='物理发布指针';

CREATE TABLE `bi_metric` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `metric_code` VARCHAR(64) NOT NULL,
  `metric_name` VARCHAR(200) NOT NULL,
  `metric_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `current_version_id` BIGINT UNSIGNED NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='稳定指标';

CREATE TABLE `bi_metric_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `metric_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `formula_ast` JSON NOT NULL,
  `time_spec_json` JSON NOT NULL,
  `currency_spec_json` JSON NOT NULL,
  `allowed_dimensions_json` JSON NOT NULL,
  `precision_scale` INT UNSIGNED NOT NULL DEFAULT 2,
  `definition_hash` CHAR(64) NOT NULL,
  `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `metric_id`, `version_no`),
  UNIQUE KEY `uk_2` (`tenant_id`, `metric_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='指标定义版本';

CREATE TABLE `bi_metric_dependency` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `metric_version_id` BIGINT UNSIGNED NOT NULL,
  `depends_metric_version_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `metric_version_id`, `depends_metric_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='指标依赖';

CREATE TABLE `bi_term` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `term_code` VARCHAR(64) NOT NULL,
  `term_name` VARCHAR(200) NOT NULL,
  `definition` TEXT NOT NULL,
  `target_asset_id` BIGINT UNSIGNED NULL,
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `term_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='业务术语';

CREATE TABLE `bi_term_synonym` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `term_id` BIGINT UNSIGNED NOT NULL,
  `synonym_text` VARCHAR(200) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `term_id`, `synonym_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='术语同义词';

SET time_zone = '+00:00';

CREATE TABLE `bi_chart` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `chart_code` VARCHAR(64) NOT NULL,
  `chart_name` VARCHAR(200) NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `current_version_id` BIGINT UNSIGNED NULL,
  `chart_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `chart_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='图表';

CREATE TABLE `bi_chart_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `chart_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `query_plan` JSON NOT NULL,
  `chart_spec` JSON NOT NULL,
  `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `chart_id`, `version_no`),
  UNIQUE KEY `uk_2` (`tenant_id`, `chart_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='图表版本';

CREATE TABLE `bi_dashboard` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `dashboard_code` VARCHAR(64) NOT NULL,
  `dashboard_name` VARCHAR(200) NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `current_version_id` BIGINT UNSIGNED NULL,
  `dashboard_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `dashboard_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='看板';

CREATE TABLE `bi_dashboard_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dashboard_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `layout_json` JSON NOT NULL,
  `filters_json` JSON NOT NULL,
  `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dashboard_id`, `version_no`),
  UNIQUE KEY `uk_2` (`tenant_id`, `dashboard_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='看板版本';

CREATE TABLE `bi_dashboard_widget` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dashboard_version_id` BIGINT UNSIGNED NOT NULL,
  `chart_version_id` BIGINT UNSIGNED NOT NULL,
  `widget_key` VARCHAR(64) NOT NULL,
  `position_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dashboard_version_id`, `widget_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='看板组件';

CREATE TABLE `bi_share` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `asset_id` BIGINT UNSIGNED NOT NULL,
  `sharer_member_id` BIGINT UNSIGNED NOT NULL,
  `recipient_member_id` BIGINT UNSIGNED NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='内部受控共享';

CREATE TABLE `bi_query_task` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `permission_version` BIGINT UNSIGNED NOT NULL,
  `generation_id` BIGINT UNSIGNED NOT NULL,
  `committed_publish_seq` BIGINT UNSIGNED NOT NULL,
  `metric_versions_json` JSON NOT NULL,
  `query_plan` JSON NOT NULL,
  `plan_hash` CHAR(64) NOT NULL,
  `engine_query_id` VARCHAR(128) NULL,
  `query_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `error_code` VARCHAR(64) NULL,
  `row_count` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `member_id`, `request_key`),
  KEY `idx_1` (`tenant_id`, `query_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='受控查询任务';

CREATE TABLE `bi_query_result_ref` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `query_task_id` BIGINT UNSIGNED NOT NULL,
  `object_file_id` BIGINT UNSIGNED NULL,
  `cache_key_hash` CHAR(64) NULL,
  `permission_version` BIGINT UNSIGNED NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `result_hash` CHAR(64) NOT NULL,
  `row_count` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `query_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='查询结果引用';

CREATE TABLE `bi_saved_query` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `query_name` VARCHAR(200) NOT NULL,
  `dataset_version_id` BIGINT UNSIGNED NOT NULL,
  `query_plan` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `workspace_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='保存查询';

CREATE TABLE `bi_report` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `report_code` VARCHAR(64) NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `report_name` VARCHAR(200) NOT NULL,
  `query_task_id` BIGINT UNSIGNED NULL,
  `content_object_id` BIGINT UNSIGNED NULL,
  `evidence_json` JSON NOT NULL,
  `report_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `workspace_id`, `report_code`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='分析报告';

CREATE TABLE `bi_export_task` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `requester_member_id` BIGINT UNSIGNED NOT NULL,
  `report_id` BIGINT UNSIGNED NULL,
  `query_task_id` BIGINT UNSIGNED NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `permission_version` BIGINT UNSIGNED NOT NULL,
  `export_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `object_file_id` BIGINT UNSIGNED NULL,
  `expires_at` DATETIME(3) NULL,
  `error_code` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `requester_member_id`, `request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='导出任务';

CREATE TABLE `bi_report_subscription` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `report_id` BIGINT UNSIGNED NOT NULL,
  `creator_member_id` BIGINT UNSIGNED NOT NULL,
  `schedule_expression` VARCHAR(128) NOT NULL,
  `timezone` VARCHAR(128) NOT NULL,
  `recipients_json` JSON NOT NULL,
  `subscription_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `next_run_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `subscription_status`, `next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='报告订阅';

CREATE TABLE `bi_report_delivery` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `report_subscription_id` BIGINT UNSIGNED NOT NULL,
  `scheduled_window` DATETIME(3) NOT NULL,
  `recipient_member_id` BIGINT UNSIGNED NOT NULL,
  `permission_version` BIGINT UNSIGNED NOT NULL,
  `export_task_id` BIGINT UNSIGNED NULL,
  `delivery_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `delivered_at` DATETIME(3) NULL,
  `error_code` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `report_subscription_id`, `scheduled_window`, `recipient_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='按收件人报告投递';

CREATE TABLE `bi_alert_rule` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `metric_version_id` BIGINT UNSIGNED NULL,
  `rule_name` VARCHAR(200) NOT NULL,
  `condition_ast` JSON NOT NULL,
  `owner_member_id` BIGINT UNSIGNED NOT NULL,
  `rule_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `workspace_id`, `rule_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='告警规则';

CREATE TABLE `bi_alert_event` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `alert_rule_id` BIGINT UNSIGNED NOT NULL,
  `dedupe_key` VARCHAR(128) NOT NULL,
  `evidence_json` JSON NOT NULL,
  `event_status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  `occurred_at` DATETIME(3) NOT NULL,
  `resolved_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `dedupe_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='告警事件';

CREATE TABLE `sys_notification` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `recipient_member_id` BIGINT UNSIGNED NOT NULL,
  `dedupe_key` VARCHAR(128) NOT NULL,
  `notification_type` VARCHAR(32) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content_summary` TEXT NULL,
  `asset_id` BIGINT UNSIGNED NULL,
  `read_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `recipient_member_id`, `dedupe_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='成员通知';

SET time_zone = '+00:00';

CREATE TABLE `ai_conversation` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `conversation_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `member_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI会话';

CREATE TABLE `ai_message` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `message_seq` BIGINT UNSIGNED NOT NULL,
  `message_role` VARCHAR(32) NOT NULL,
  `content_redacted` TEXT NULL,
  `content_object_id` BIGINT UNSIGNED NULL,
  `query_task_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `conversation_id`, `message_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI会话消息';

CREATE TABLE `ai_task` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `graph_thread_key` VARCHAR(255) NOT NULL,
  `checkpoint_ref` VARCHAR(512) NULL,
  `permission_version` BIGINT UNSIGNED NOT NULL,
  `task_status` VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
  `plan_summary` TEXT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `deadline_at` DATETIME(3) NOT NULL,
  `error_code` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `member_id`, `request_key`),
  UNIQUE KEY `uk_2` (`tenant_id`, `graph_thread_key`),
  KEY `idx_1` (`tenant_id`, `task_status`, `deadline_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI工作流任务';

CREATE TABLE `ai_tool_invocation` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ai_task_id` BIGINT UNSIGNED NOT NULL,
  `call_seq` INT UNSIGNED NOT NULL,
  `tool_name` VARCHAR(64) NOT NULL,
  `arguments_hash` CHAR(64) NOT NULL,
  `query_task_id` BIGINT UNSIGNED NULL,
  `result_summary` JSON NULL,
  `invocation_status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `ai_task_id`, `call_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI受控工具调用';

CREATE TABLE `ai_model_config` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `config_name` VARCHAR(64) NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(128) NOT NULL,
  `credential_ref` VARCHAR(255) NULL,
  `allowed_regions_json` JSON NOT NULL,
  `data_policy_json` JSON NOT NULL,
  `external_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `config_status` VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='模型外发配置';

CREATE TABLE `ai_usage_ledger` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ai_task_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `provider_request_id` VARCHAR(128) NOT NULL,
  `model_name` VARCHAR(128) NOT NULL,
  `prompt_tokens` BIGINT UNSIGNED NOT NULL,
  `completion_tokens` BIGINT UNSIGNED NOT NULL,
  `amount_minor_units` BIGINT NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `is_estimated` TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `occurred_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `provider_code`, `provider_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI供应商费用流水';

CREATE TABLE `ai_feedback` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ai_task_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `rating` TINYINT UNSIGNED NOT NULL,
  `comment_redacted` TEXT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `ai_task_id`, `member_id`),
  CONSTRAINT `ck_ai_feedback_1` CHECK (rating IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='AI质量反馈';

CREATE TABLE `saas_plan` (
  `id` BIGINT UNSIGNED NOT NULL,
  `plan_code` VARCHAR(64) NOT NULL,
  `plan_name` VARCHAR(200) NOT NULL,
  `plan_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='全局套餐';

CREATE TABLE `saas_plan_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `plan_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `price_minor_units` BIGINT NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `billing_period` VARCHAR(32) NOT NULL,
  `quota_json` JSON NOT NULL,
  `version_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `effective_from` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`plan_id`, `version_no`),
  CONSTRAINT `ck_saas_plan_version_1` CHECK (price_minor_units >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='套餐价格版本';

CREATE TABLE `saas_subscription` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `plan_version_id` BIGINT UNSIGNED NOT NULL,
  `subscription_status` VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
  `period_start` DATETIME(3) NOT NULL,
  `period_end` DATETIME(3) NOT NULL,
  `cancel_at_period_end` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `subscription_status`, `period_end`),
  CONSTRAINT `ck_saas_subscription_1` CHECK (period_end > period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户订阅';

CREATE TABLE `saas_order` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `subscription_id` BIGINT UNSIGNED NOT NULL,
  `order_no` VARCHAR(128) NOT NULL,
  `plan_version_id` BIGINT UNSIGNED NOT NULL,
  `amount_minor_units` BIGINT NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `price_snapshot` JSON NOT NULL,
  `order_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `paid_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `order_no`),
  CONSTRAINT `ck_saas_order_1` CHECK (amount_minor_units >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='订阅订单';

CREATE TABLE `saas_payment_event` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(64) NOT NULL,
  `provider_event_id` VARCHAR(128) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `amount_minor_units` BIGINT NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `verified_at` DATETIME(3) NOT NULL,
  `payload_hash` CHAR(64) NOT NULL,
  `processed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`provider_code`, `provider_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='支付幂等事件';

CREATE TABLE `saas_quota_balance` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `quota_code` VARCHAR(64) NOT NULL,
  `window_start` DATETIME(3) NOT NULL,
  `window_end` DATETIME(3) NOT NULL,
  `quota_limit` BIGINT UNSIGNED NOT NULL,
  `used_amount` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `reserved_amount` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `quota_code`, `window_start`),
  CONSTRAINT `ck_saas_quota_balance_1` CHECK (used_amount + reserved_amount <= quota_limit),
  CONSTRAINT `ck_saas_quota_balance_2` CHECK (window_end > window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='配额余额';

CREATE TABLE `saas_quota_ledger` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `quota_balance_id` BIGINT UNSIGNED NOT NULL,
  `business_event_key` VARCHAR(128) NOT NULL,
  `reservation_key` VARCHAR(128) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `amount` BIGINT UNSIGNED NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `business_event_key`),
  KEY `idx_1` (`tenant_id`, `reservation_key`),
  CONSTRAINT `ck_saas_quota_ledger_1` CHECK (event_type IN ('RESERVE','CONFIRM','RELEASE')),
  CONSTRAINT `ck_saas_quota_ledger_2` CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='配额幂等流水';

CREATE TABLE `sys_object_file` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `workspace_id` BIGINT UNSIGNED NULL,
  `owner_member_id` BIGINT UNSIGNED NULL,
  `object_key` VARCHAR(512) NOT NULL,
  `bucket_name` VARCHAR(128) NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(128) NOT NULL,
  `size_bytes` BIGINT UNSIGNED NOT NULL,
  `content_hash` CHAR(64) NOT NULL,
  `purpose` VARCHAR(32) NOT NULL,
  `file_status` VARCHAR(32) NOT NULL DEFAULT 'QUARANTINED',
  `expires_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='对象文件元数据';

CREATE TABLE `sys_audit_event` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `actor_user_id` BIGINT UNSIGNED NULL,
  `actor_member_id` BIGINT UNSIGNED NULL,
  `action_code` VARCHAR(128) NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_id` BIGINT UNSIGNED NULL,
  `permission_version` BIGINT UNSIGNED NULL,
  `trace_id` VARCHAR(64) NOT NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `summary_json` JSON NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `occurred_at`),
  KEY `idx_2` (`tenant_id`, `trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='追加审计';

CREATE TABLE `sys_outbox_event` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `event_key` VARCHAR(128) NOT NULL,
  `aggregate_type` VARCHAR(32) NOT NULL,
  `aggregate_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(128) NOT NULL,
  `schema_version` INT UNSIGNED NOT NULL DEFAULT 1,
  `payload_json` JSON NOT NULL,
  `event_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `available_at` DATETIME(3) NOT NULL,
  `published_at` DATETIME(3) NULL,
  `lease_owner` VARCHAR(128) NULL,
  `lease_until` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `event_key`),
  KEY `idx_1` (`event_status`, `available_at`),
  KEY `idx_2` (`tenant_id`, `aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='事务消息箱';

CREATE TABLE `sys_inbox_dedup` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `consumer_code` VARCHAR(64) NOT NULL,
  `event_key` VARCHAR(128) NOT NULL,
  `processed_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `consumer_code`, `event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='消费者幂等';

CREATE TABLE `sys_idempotency_request` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `api_route` VARCHAR(128) NOT NULL,
  `idempotency_key` VARCHAR(128) NOT NULL,
  `request_hash` CHAR(64) NOT NULL,
  `request_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `response_ref` JSON NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_1` (`tenant_id`, `member_id`, `api_route`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='接口幂等';

CREATE TABLE `sys_support_grant` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `approver_member_id` BIGINT UNSIGNED NOT NULL,
  `support_user_id` BIGINT UNSIGNED NOT NULL,
  `ticket_code` VARCHAR(128) NOT NULL,
  `scope_json` JSON NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `support_user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='临时支持授权';

CREATE TABLE `sys_tenant_exit` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `requester_member_id` BIGINT UNSIGNED NOT NULL,
  `exit_status` VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
  `requested_at` DATETIME(3) NOT NULL,
  `revocable_until` DATETIME(3) NOT NULL,
  `online_deleted_at` DATETIME(3) NULL,
  `backup_expire_at` DATETIME(3) NULL,
  `tombstone_hash` CHAR(64) NULL,
  `export_object_id` BIGINT UNSIGNED NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_by` BIGINT UNSIGNED NULL,
  `updated_by` BIGINT UNSIGNED NULL,
  `lock_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`, `id`),
  KEY `idx_1` (`tenant_id`, `exit_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='租户退出与墓碑';

ALTER TABLE `sys_user` ADD CONSTRAINT `fk_001` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_user` ADD CONSTRAINT `fk_002` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant` ADD CONSTRAINT `fk_003` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant` ADD CONSTRAINT `fk_004` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_login_session` ADD CONSTRAINT `fk_005` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_login_session` ADD CONSTRAINT `fk_006` FOREIGN KEY (`rotated_from_id`) REFERENCES `sys_login_session` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_login_session` ADD CONSTRAINT `fk_007` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_login_session` ADD CONSTRAINT `fk_008` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_member` ADD CONSTRAINT `fk_009` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_member` ADD CONSTRAINT `fk_010` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_member` ADD CONSTRAINT `fk_011` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_member` ADD CONSTRAINT `fk_012` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_department` ADD CONSTRAINT `fk_013` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_department` ADD CONSTRAINT `fk_014` FOREIGN KEY (`tenant_id`, `parent_id`) REFERENCES `sys_department` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_department` ADD CONSTRAINT `fk_015` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_department` ADD CONSTRAINT `fk_016` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_department` ADD CONSTRAINT `fk_017` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_department` ADD CONSTRAINT `fk_018` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_department` ADD CONSTRAINT `fk_019` FOREIGN KEY (`tenant_id`, `department_id`) REFERENCES `sys_department` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_department` ADD CONSTRAINT `fk_020` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_department` ADD CONSTRAINT `fk_021` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace` ADD CONSTRAINT `fk_022` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace` ADD CONSTRAINT `fk_023` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace` ADD CONSTRAINT `fk_024` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace` ADD CONSTRAINT `fk_025` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace_member` ADD CONSTRAINT `fk_026` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace_member` ADD CONSTRAINT `fk_027` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace_member` ADD CONSTRAINT `fk_028` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace_member` ADD CONSTRAINT `fk_029` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_workspace_member` ADD CONSTRAINT `fk_030` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_031` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_032` FOREIGN KEY (`tenant_id`, `inviter_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_033` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_034` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_035` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_invitation` ADD CONSTRAINT `fk_036` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_setting` ADD CONSTRAINT `fk_037` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_setting` ADD CONSTRAINT `fk_038` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_setting` ADD CONSTRAINT `fk_039` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_resource` ADD CONSTRAINT `fk_040` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_resource` ADD CONSTRAINT `fk_041` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_resource` ADD CONSTRAINT `fk_042` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_permission` ADD CONSTRAINT `fk_043` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_permission` ADD CONSTRAINT `fk_044` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role` ADD CONSTRAINT `fk_045` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role` ADD CONSTRAINT `fk_046` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role` ADD CONSTRAINT `fk_047` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role_permission` ADD CONSTRAINT `fk_048` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role_permission` ADD CONSTRAINT `fk_049` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role_permission` ADD CONSTRAINT `fk_050` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role_permission` ADD CONSTRAINT `fk_051` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_role_permission` ADD CONSTRAINT `fk_052` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_053` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_054` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_055` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_056` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_057` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_member_role` ADD CONSTRAINT `fk_058` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_059` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_060` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_061` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_062` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_063` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset` ADD CONSTRAINT `fk_064` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset_acl` ADD CONSTRAINT `fk_065` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset_acl` ADD CONSTRAINT `fk_066` FOREIGN KEY (`tenant_id`, `asset_id`) REFERENCES `sys_asset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset_acl` ADD CONSTRAINT `fk_067` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_asset_acl` ADD CONSTRAINT `fk_068` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_data_policy` ADD CONSTRAINT `fk_069` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_data_policy` ADD CONSTRAINT `fk_070` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_data_policy` ADD CONSTRAINT `fk_071` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_data_policy` ADD CONSTRAINT `fk_072` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_policy_grant` ADD CONSTRAINT `fk_073` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_policy_grant` ADD CONSTRAINT `fk_074` FOREIGN KEY (`tenant_id`, `policy_id`) REFERENCES `sys_data_policy` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_policy_grant` ADD CONSTRAINT `fk_075` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_policy_grant` ADD CONSTRAINT `fk_076` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_column_policy` ADD CONSTRAINT `fk_077` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_column_policy` ADD CONSTRAINT `fk_078` FOREIGN KEY (`tenant_id`, `dataset_field_id`) REFERENCES `bi_dataset_field` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_column_policy` ADD CONSTRAINT `fk_079` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_column_policy` ADD CONSTRAINT `fk_080` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_identity_provider` ADD CONSTRAINT `fk_081` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_identity_provider` ADD CONSTRAINT `fk_082` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_identity_provider` ADD CONSTRAINT `fk_083` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_datasource` ADD CONSTRAINT `fk_084` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_datasource` ADD CONSTRAINT `fk_085` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_datasource` ADD CONSTRAINT `fk_086` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_datasource` ADD CONSTRAINT `fk_datasource_owner_member` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_datasource` ADD CONSTRAINT `fk_087` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_object` ADD CONSTRAINT `fk_088` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_object` ADD CONSTRAINT `fk_089` FOREIGN KEY (`tenant_id`, `datasource_id`) REFERENCES `bi_datasource` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_object` ADD CONSTRAINT `fk_090` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_object` ADD CONSTRAINT `fk_091` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_column` ADD CONSTRAINT `fk_092` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_column` ADD CONSTRAINT `fk_093` FOREIGN KEY (`tenant_id`, `source_object_id`) REFERENCES `bi_source_object` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_column` ADD CONSTRAINT `fk_094` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_source_column` ADD CONSTRAINT `fk_095` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metadata_snapshot` ADD CONSTRAINT `fk_096` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metadata_snapshot` ADD CONSTRAINT `fk_097` FOREIGN KEY (`tenant_id`, `datasource_id`) REFERENCES `bi_datasource` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metadata_snapshot` ADD CONSTRAINT `fk_098` FOREIGN KEY (`tenant_id`, `diff_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metadata_snapshot` ADD CONSTRAINT `fk_099` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metadata_snapshot` ADD CONSTRAINT `fk_100` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_101` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_102` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_103` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_104` FOREIGN KEY (`tenant_id`, `object_file_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_105` FOREIGN KEY (`tenant_id`, `requester_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_106` FOREIGN KEY (`tenant_id`, `error_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_107` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_import_job` ADD CONSTRAINT `fk_108` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_job` ADD CONSTRAINT `fk_109` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_job` ADD CONSTRAINT `fk_110` FOREIGN KEY (`tenant_id`, `datasource_id`) REFERENCES `bi_datasource` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_job` ADD CONSTRAINT `fk_111` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_job` ADD CONSTRAINT `fk_112` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_job` ADD CONSTRAINT `fk_113` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_watermark` ADD CONSTRAINT `fk_114` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_watermark` ADD CONSTRAINT `fk_115` FOREIGN KEY (`tenant_id`, `sync_job_id`) REFERENCES `bi_sync_job` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_watermark` ADD CONSTRAINT `fk_116` FOREIGN KEY (`tenant_id`, `last_batch_id`) REFERENCES `bi_batch` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_watermark` ADD CONSTRAINT `fk_117` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_watermark` ADD CONSTRAINT `fk_118` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_run` ADD CONSTRAINT `fk_119` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_run` ADD CONSTRAINT `fk_120` FOREIGN KEY (`tenant_id`, `sync_job_id`) REFERENCES `bi_sync_job` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_run` ADD CONSTRAINT `fk_121` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_sync_run` ADD CONSTRAINT `fk_122` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_123` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_124` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_125` FOREIGN KEY (`tenant_id`, `sync_run_id`) REFERENCES `bi_sync_run` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_126` FOREIGN KEY (`tenant_id`, `import_job_id`) REFERENCES `bi_import_job` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_127` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_batch` ADD CONSTRAINT `fk_128` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_rule` ADD CONSTRAINT `fk_129` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_rule` ADD CONSTRAINT `fk_130` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_rule` ADD CONSTRAINT `fk_131` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_rule` ADD CONSTRAINT `fk_132` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_133` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_134` FOREIGN KEY (`tenant_id`, `batch_id`) REFERENCES `bi_batch` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_135` FOREIGN KEY (`tenant_id`, `quality_rule_id`) REFERENCES `bi_quality_rule` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_136` FOREIGN KEY (`tenant_id`, `evidence_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_137` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_quality_result` ADD CONSTRAINT `fk_138` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_139` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_140` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_141` FOREIGN KEY (`tenant_id`, `current_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_142` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_143` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_144` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_145` FOREIGN KEY (`tenant_id`, `id`, `current_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `dataset_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset` ADD CONSTRAINT `fk_146` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_version` ADD CONSTRAINT `fk_147` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_version` ADD CONSTRAINT `fk_148` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_version` ADD CONSTRAINT `fk_149` FOREIGN KEY (`tenant_id`, `source_object_id`) REFERENCES `bi_source_object` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_version` ADD CONSTRAINT `fk_150` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_version` ADD CONSTRAINT `fk_151` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_field` ADD CONSTRAINT `fk_152` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_field` ADD CONSTRAINT `fk_153` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_field` ADD CONSTRAINT `fk_154` FOREIGN KEY (`tenant_id`, `source_column_id`) REFERENCES `bi_source_column` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_field` ADD CONSTRAINT `fk_155` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_field` ADD CONSTRAINT `fk_156` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_relation` ADD CONSTRAINT `fk_157` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_relation` ADD CONSTRAINT `fk_158` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_relation` ADD CONSTRAINT `fk_159` FOREIGN KEY (`tenant_id`, `related_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_relation` ADD CONSTRAINT `fk_160` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_relation` ADD CONSTRAINT `fk_161` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_162` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_163` FOREIGN KEY (`tenant_id`, `dataset_id`) REFERENCES `bi_dataset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_164` FOREIGN KEY (`tenant_id`, `last_batch_id`) REFERENCES `bi_batch` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_165` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_166` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dataset_publication` ADD CONSTRAINT `fk_167` FOREIGN KEY (`tenant_id`, `dataset_id`, `active_generation`, `committed_publish_seq`, `last_batch_id`) REFERENCES `bi_batch` (`tenant_id`, `dataset_id`, `generation_id`, `publish_seq`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_168` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_169` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_170` FOREIGN KEY (`tenant_id`, `current_version_id`) REFERENCES `bi_metric_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_171` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_172` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_173` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_174` FOREIGN KEY (`tenant_id`, `id`, `current_version_id`) REFERENCES `bi_metric_version` (`tenant_id`, `metric_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric` ADD CONSTRAINT `fk_175` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_version` ADD CONSTRAINT `fk_176` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_version` ADD CONSTRAINT `fk_177` FOREIGN KEY (`tenant_id`, `metric_id`) REFERENCES `bi_metric` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_version` ADD CONSTRAINT `fk_178` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_version` ADD CONSTRAINT `fk_179` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_version` ADD CONSTRAINT `fk_180` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_dependency` ADD CONSTRAINT `fk_181` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_dependency` ADD CONSTRAINT `fk_182` FOREIGN KEY (`tenant_id`, `metric_version_id`) REFERENCES `bi_metric_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_dependency` ADD CONSTRAINT `fk_183` FOREIGN KEY (`tenant_id`, `depends_metric_version_id`) REFERENCES `bi_metric_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_dependency` ADD CONSTRAINT `fk_184` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_metric_dependency` ADD CONSTRAINT `fk_185` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term` ADD CONSTRAINT `fk_186` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term` ADD CONSTRAINT `fk_187` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term` ADD CONSTRAINT `fk_188` FOREIGN KEY (`tenant_id`, `target_asset_id`) REFERENCES `sys_asset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term` ADD CONSTRAINT `fk_189` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term` ADD CONSTRAINT `fk_190` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term_synonym` ADD CONSTRAINT `fk_191` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term_synonym` ADD CONSTRAINT `fk_192` FOREIGN KEY (`tenant_id`, `term_id`) REFERENCES `bi_term` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term_synonym` ADD CONSTRAINT `fk_193` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_term_synonym` ADD CONSTRAINT `fk_194` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_195` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_196` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_197` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_198` FOREIGN KEY (`tenant_id`, `current_version_id`) REFERENCES `bi_chart_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_199` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_200` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_201` FOREIGN KEY (`tenant_id`, `id`, `current_version_id`) REFERENCES `bi_chart_version` (`tenant_id`, `chart_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart` ADD CONSTRAINT `fk_202` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart_version` ADD CONSTRAINT `fk_203` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart_version` ADD CONSTRAINT `fk_204` FOREIGN KEY (`tenant_id`, `chart_id`) REFERENCES `bi_chart` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart_version` ADD CONSTRAINT `fk_205` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart_version` ADD CONSTRAINT `fk_206` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_chart_version` ADD CONSTRAINT `fk_207` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_208` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_209` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_210` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_211` FOREIGN KEY (`tenant_id`, `current_version_id`) REFERENCES `bi_dashboard_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_212` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_213` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_214` FOREIGN KEY (`tenant_id`, `id`, `current_version_id`) REFERENCES `bi_dashboard_version` (`tenant_id`, `dashboard_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard` ADD CONSTRAINT `fk_215` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_version` ADD CONSTRAINT `fk_216` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_version` ADD CONSTRAINT `fk_217` FOREIGN KEY (`tenant_id`, `dashboard_id`) REFERENCES `bi_dashboard` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_version` ADD CONSTRAINT `fk_218` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_version` ADD CONSTRAINT `fk_219` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_widget` ADD CONSTRAINT `fk_220` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_widget` ADD CONSTRAINT `fk_221` FOREIGN KEY (`tenant_id`, `dashboard_version_id`) REFERENCES `bi_dashboard_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_widget` ADD CONSTRAINT `fk_222` FOREIGN KEY (`tenant_id`, `chart_version_id`) REFERENCES `bi_chart_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_widget` ADD CONSTRAINT `fk_223` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_dashboard_widget` ADD CONSTRAINT `fk_224` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_225` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_226` FOREIGN KEY (`tenant_id`, `asset_id`) REFERENCES `sys_asset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_227` FOREIGN KEY (`tenant_id`, `sharer_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_228` FOREIGN KEY (`tenant_id`, `recipient_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_229` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_share` ADD CONSTRAINT `fk_230` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_231` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_232` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_233` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_234` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_235` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_task` ADD CONSTRAINT `fk_236` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_result_ref` ADD CONSTRAINT `fk_237` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_result_ref` ADD CONSTRAINT `fk_238` FOREIGN KEY (`tenant_id`, `query_task_id`) REFERENCES `bi_query_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_result_ref` ADD CONSTRAINT `fk_239` FOREIGN KEY (`tenant_id`, `object_file_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_result_ref` ADD CONSTRAINT `fk_240` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_query_result_ref` ADD CONSTRAINT `fk_241` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_242` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_243` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_244` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_245` FOREIGN KEY (`tenant_id`, `dataset_version_id`) REFERENCES `bi_dataset_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_246` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_247` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_saved_query` ADD CONSTRAINT `fk_248` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_249` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_250` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_251` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_252` FOREIGN KEY (`tenant_id`, `query_task_id`) REFERENCES `bi_query_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_253` FOREIGN KEY (`tenant_id`, `content_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_254` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_255` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report` ADD CONSTRAINT `fk_256` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_257` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_258` FOREIGN KEY (`tenant_id`, `requester_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_259` FOREIGN KEY (`tenant_id`, `report_id`) REFERENCES `bi_report` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_260` FOREIGN KEY (`tenant_id`, `query_task_id`) REFERENCES `bi_query_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_261` FOREIGN KEY (`tenant_id`, `object_file_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_262` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_export_task` ADD CONSTRAINT `fk_263` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_subscription` ADD CONSTRAINT `fk_264` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_subscription` ADD CONSTRAINT `fk_265` FOREIGN KEY (`tenant_id`, `report_id`) REFERENCES `bi_report` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_subscription` ADD CONSTRAINT `fk_266` FOREIGN KEY (`tenant_id`, `creator_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_subscription` ADD CONSTRAINT `fk_267` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_subscription` ADD CONSTRAINT `fk_268` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_269` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_270` FOREIGN KEY (`tenant_id`, `report_subscription_id`) REFERENCES `bi_report_subscription` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_271` FOREIGN KEY (`tenant_id`, `recipient_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_272` FOREIGN KEY (`tenant_id`, `export_task_id`) REFERENCES `bi_export_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_273` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_report_delivery` ADD CONSTRAINT `fk_274` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_275` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_276` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_277` FOREIGN KEY (`tenant_id`, `metric_version_id`) REFERENCES `bi_metric_version` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_278` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_279` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_280` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_rule` ADD CONSTRAINT `fk_281` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_event` ADD CONSTRAINT `fk_282` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_event` ADD CONSTRAINT `fk_283` FOREIGN KEY (`tenant_id`, `alert_rule_id`) REFERENCES `bi_alert_rule` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_event` ADD CONSTRAINT `fk_284` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `bi_alert_event` ADD CONSTRAINT `fk_285` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_notification` ADD CONSTRAINT `fk_286` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_notification` ADD CONSTRAINT `fk_287` FOREIGN KEY (`tenant_id`, `recipient_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_notification` ADD CONSTRAINT `fk_288` FOREIGN KEY (`tenant_id`, `asset_id`) REFERENCES `sys_asset` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_notification` ADD CONSTRAINT `fk_289` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_notification` ADD CONSTRAINT `fk_290` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_conversation` ADD CONSTRAINT `fk_291` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_conversation` ADD CONSTRAINT `fk_292` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_conversation` ADD CONSTRAINT `fk_293` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_conversation` ADD CONSTRAINT `fk_294` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_conversation` ADD CONSTRAINT `fk_295` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_message` ADD CONSTRAINT `fk_296` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_message` ADD CONSTRAINT `fk_297` FOREIGN KEY (`tenant_id`, `conversation_id`) REFERENCES `ai_conversation` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_message` ADD CONSTRAINT `fk_298` FOREIGN KEY (`tenant_id`, `content_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_message` ADD CONSTRAINT `fk_299` FOREIGN KEY (`tenant_id`, `query_task_id`) REFERENCES `bi_query_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;


ALTER TABLE `ai_message` ADD CONSTRAINT `fk_300` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_message` ADD CONSTRAINT `fk_301` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_task` ADD CONSTRAINT `fk_302` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_task` ADD CONSTRAINT `fk_303` FOREIGN KEY (`tenant_id`, `conversation_id`) REFERENCES `ai_conversation` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_task` ADD CONSTRAINT `fk_304` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_task` ADD CONSTRAINT `fk_305` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_task` ADD CONSTRAINT `fk_306` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;


ALTER TABLE `ai_tool_invocation` ADD CONSTRAINT `fk_307` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_tool_invocation` ADD CONSTRAINT `fk_308` FOREIGN KEY (`tenant_id`, `ai_task_id`) REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_tool_invocation` ADD CONSTRAINT `fk_309` FOREIGN KEY (`tenant_id`, `query_task_id`) REFERENCES `bi_query_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_tool_invocation` ADD CONSTRAINT `fk_310` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_tool_invocation` ADD CONSTRAINT `fk_311` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_model_config` ADD CONSTRAINT `fk_312` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_model_config` ADD CONSTRAINT `fk_313` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_model_config` ADD CONSTRAINT `fk_314` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_usage_ledger` ADD CONSTRAINT `fk_315` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_usage_ledger` ADD CONSTRAINT `fk_316` FOREIGN KEY (`tenant_id`, `ai_task_id`) REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_usage_ledger` ADD CONSTRAINT `fk_317` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_usage_ledger` ADD CONSTRAINT `fk_318` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_feedback` ADD CONSTRAINT `fk_319` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_feedback` ADD CONSTRAINT `fk_320` FOREIGN KEY (`tenant_id`, `ai_task_id`) REFERENCES `ai_task` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_feedback` ADD CONSTRAINT `fk_321` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_feedback` ADD CONSTRAINT `fk_322` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `ai_feedback` ADD CONSTRAINT `fk_323` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_plan` ADD CONSTRAINT `fk_324` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_plan` ADD CONSTRAINT `fk_325` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_plan_version` ADD CONSTRAINT `fk_326` FOREIGN KEY (`plan_id`) REFERENCES `saas_plan` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_plan_version` ADD CONSTRAINT `fk_327` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_plan_version` ADD CONSTRAINT `fk_328` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_subscription` ADD CONSTRAINT `fk_329` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_subscription` ADD CONSTRAINT `fk_330` FOREIGN KEY (`plan_version_id`) REFERENCES `saas_plan_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_subscription` ADD CONSTRAINT `fk_331` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_subscription` ADD CONSTRAINT `fk_332` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_order` ADD CONSTRAINT `fk_333` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_order` ADD CONSTRAINT `fk_334` FOREIGN KEY (`tenant_id`, `subscription_id`) REFERENCES `saas_subscription` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_order` ADD CONSTRAINT `fk_335` FOREIGN KEY (`plan_version_id`) REFERENCES `saas_plan_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_order` ADD CONSTRAINT `fk_336` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_order` ADD CONSTRAINT `fk_337` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_payment_event` ADD CONSTRAINT `fk_338` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_payment_event` ADD CONSTRAINT `fk_339` FOREIGN KEY (`tenant_id`, `order_id`) REFERENCES `saas_order` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_payment_event` ADD CONSTRAINT `fk_340` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_payment_event` ADD CONSTRAINT `fk_341` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_balance` ADD CONSTRAINT `fk_342` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_balance` ADD CONSTRAINT `fk_343` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_balance` ADD CONSTRAINT `fk_344` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_ledger` ADD CONSTRAINT `fk_345` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_ledger` ADD CONSTRAINT `fk_346` FOREIGN KEY (`tenant_id`, `quota_balance_id`) REFERENCES `saas_quota_balance` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_ledger` ADD CONSTRAINT `fk_347` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `saas_quota_ledger` ADD CONSTRAINT `fk_348` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_349` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_350` FOREIGN KEY (`tenant_id`, `workspace_id`) REFERENCES `sys_workspace` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_351` FOREIGN KEY (`tenant_id`, `owner_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_352` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_353` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_object_file` ADD CONSTRAINT `fk_354` FOREIGN KEY (`tenant_id`, `workspace_id`, `owner_member_id`) REFERENCES `sys_workspace_member` (`tenant_id`, `workspace_id`, `member_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_audit_event` ADD CONSTRAINT `fk_355` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_audit_event` ADD CONSTRAINT `fk_356` FOREIGN KEY (`actor_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_audit_event` ADD CONSTRAINT `fk_357` FOREIGN KEY (`tenant_id`, `actor_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_audit_event` ADD CONSTRAINT `fk_358` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_audit_event` ADD CONSTRAINT `fk_359` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_outbox_event` ADD CONSTRAINT `fk_360` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_outbox_event` ADD CONSTRAINT `fk_361` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_outbox_event` ADD CONSTRAINT `fk_362` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_inbox_dedup` ADD CONSTRAINT `fk_363` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_inbox_dedup` ADD CONSTRAINT `fk_364` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_inbox_dedup` ADD CONSTRAINT `fk_365` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_idempotency_request` ADD CONSTRAINT `fk_366` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_idempotency_request` ADD CONSTRAINT `fk_367` FOREIGN KEY (`tenant_id`, `member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_idempotency_request` ADD CONSTRAINT `fk_368` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_idempotency_request` ADD CONSTRAINT `fk_369` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_support_grant` ADD CONSTRAINT `fk_370` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_support_grant` ADD CONSTRAINT `fk_371` FOREIGN KEY (`tenant_id`, `approver_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_support_grant` ADD CONSTRAINT `fk_372` FOREIGN KEY (`support_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_support_grant` ADD CONSTRAINT `fk_373` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_support_grant` ADD CONSTRAINT `fk_374` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_exit` ADD CONSTRAINT `fk_375` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_exit` ADD CONSTRAINT `fk_376` FOREIGN KEY (`tenant_id`, `requester_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_exit` ADD CONSTRAINT `fk_377` FOREIGN KEY (`tenant_id`, `export_object_id`) REFERENCES `sys_object_file` (`tenant_id`, `id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_exit` ADD CONSTRAINT `fk_378` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `sys_tenant_exit` ADD CONSTRAINT `fk_379` FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, action_code) VALUES
(1,'tenant:manage','管理企业','TENANT','manage'),
(2,'member:manage','管理成员','MEMBER','manage'),
(3,'workspace:manage','管理空间','WORKSPACE','manage'),
(4,'role:manage','管理角色','ROLE','manage'),
(5,'policy:manage','管理数据权限','POLICY','manage'),
(6,'datasource:manage','管理数据源','DATASOURCE','manage'),
(7,'dataset:read','读取数据集','DATASET','read'),
(8,'dataset:manage','管理数据集','DATASET','manage'),
(9,'dataset:publish','发布数据集','DATASET','publish'),
(10,'metric:read','读取指标','METRIC','read'),
(11,'metric:manage','管理指标','METRIC','manage'),
(12,'query:execute','受控查询','QUERY','execute'),
(13,'chart:manage','管理图表','CHART','manage'),
(14,'dashboard:read','查看看板','DASHBOARD','read'),
(15,'dashboard:manage','管理看板','DASHBOARD','manage'),
(16,'report:export','导出报告','REPORT','export'),
(17,'ai:ask','AI问答','AI','ask'),
(18,'audit:read','查看租户审计','AUDIT','read'),
(19,'subscription:manage','管理订阅','SUBSCRIPTION','manage'),
(20,'tenant:exit','申请企业退出','TENANT','exit');
