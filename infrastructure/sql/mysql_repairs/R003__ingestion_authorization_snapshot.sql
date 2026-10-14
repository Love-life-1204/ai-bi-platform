-- Forward-only repair namespace. Apply only through audited repair runner after backup/restore verification.
-- This is a new D07 dependency, separate from D10's original 13 missing business tables.
CREATE TABLE bi_ingestion_auth_snapshot (
    tenant_id BIGINT UNSIGNED NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id, task_type, task_id),
    CONSTRAINT fk_ingestion_auth_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
