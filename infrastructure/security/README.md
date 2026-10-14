# 运行账号最小权限

迁移账号与应用账号必须分离；应用配置中禁止使用 `root`、`default` 或 `bi_migrator`。

## MySQL

`provision_mysql_roles.py` 创建 `aibi_api_role`，按表授予运行所需的 DML，不授予 DDL、GRANT 或全局权限。由管理员通过密钥管理流程创建登录账号，然后执行：

```sql
GRANT 'aibi_api_role' TO 'aibi_api'@'<application-host>';
SET DEFAULT ROLE 'aibi_api_role' TO 'aibi_api'@'<application-host>';
```

禁止使用 `%` 作为生产账号来源主机；定期使用 `SHOW GRANTS` 审计额外权限。

## ClickHouse

执行 `python infrastructure/scripts/provision_clickhouse_roles.py`创建 `aibi_query_role`。该角色仅可读 `ai_bi_dwd.*` 和 `ai_bi_dws.*`，不可读 RAW 层，不可写入或执行 DDL。管理员另行创建 `aibi_reader` 并授予该角色：

```sql
GRANT aibi_query_role TO aibi_reader;
ALTER USER aibi_reader DEFAULT ROLE aibi_query_role;
```

密码不写入 SQL 或 Git，由部署平台注入 `MYSQL_APP_PASSWORD` 和 `CLICKHOUSE_APP_PASSWORD`。

同步 Worker 使用独立的 `aibi_ingestion_worker` 登录账号和 `aibi_ingestion_role`，仅允许向
`ai_bi_raw.ingest_event` 与 `ai_bi_ops.batch_ingest_receipt` 执行 `INSERT`。不得给该账号授予
`SELECT`、DDL、系统表或 DWD/DWS 直接写权限：

```sql
GRANT aibi_ingestion_role TO aibi_ingestion_worker;
ALTER USER aibi_ingestion_worker DEFAULT ROLE aibi_ingestion_role;
```

Writer 密钥通过 `CLICKHOUSE_WRITER_PASSWORD` 单独注入，不与查询账号共用。
