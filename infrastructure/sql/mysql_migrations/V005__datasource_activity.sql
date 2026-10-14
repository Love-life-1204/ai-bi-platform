-- 新安装的基线已包含这些字段；旧安装则由本迁移前向补齐。
SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='bi_datasource'
      AND column_name='owner_member_id'),
  'SELECT 1',
  'ALTER TABLE bi_datasource ADD COLUMN owner_member_id BIGINT UNSIGNED NULL AFTER credential_ref'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='bi_datasource'
      AND column_name='last_test_status'),
  'SELECT 1',
  'ALTER TABLE bi_datasource ADD COLUMN last_test_status VARCHAR(64) NULL AFTER owner_member_id'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='bi_datasource'
      AND column_name='last_test_at'),
  'SELECT 1',
  'ALTER TABLE bi_datasource ADD COLUMN last_test_at DATETIME(3) NULL AFTER last_test_status'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='bi_datasource'
      AND index_name='idx_owner_member'),
  'SELECT 1',
  'ALTER TABLE bi_datasource ADD KEY idx_owner_member (tenant_id, owner_member_id)'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE() AND table_name='bi_datasource'
      AND constraint_name='fk_datasource_owner_member'),
  'SELECT 1',
  'ALTER TABLE bi_datasource ADD CONSTRAINT fk_datasource_owner_member FOREIGN KEY (tenant_id, owner_member_id) REFERENCES sys_tenant_member (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT'
);
PREPARE migration_statement FROM @ddl;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
