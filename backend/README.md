# Java API 公共基线

后端使用 Java 21 与 Spring Boot 3.5，包结构固定为：

`Controller → Service → ServiceImpl → Manager → Mapper → Entity`

- Controller 只处理 HTTP 协议、参数校验和响应适配。
- Service 接口定义业务用例；ServiceImpl 负责事务边界和用例编排。
- Manager 封装跨资源协调、受控查询、幂等或外部适配。
- Mapper 只负责数据访问；Entity 只表达持久化对象。
- 对外对象使用 DTO，不直接暴露 Entity。

所有 `/api/**` JSON 响应统一为：

```json
{
  "code": "SUCCESS",
  "message": "请求成功",
  "data": {},
  "trace_id": "e4a94a6a96a242cfa017e11996538eb2"
}
```

公共业务接口的正式路径不包含 URL 版本号。迁移期仍接受旧的 `/api/v1/**` 请求，
但响应会携带 `Deprecation: true` 和 `Warning` 头；旧路径不会出现在 OpenAPI 文档中。
新客户端必须只使用 `/api/**`。

客户端可传入 8—64 位安全字符组成的 `X-Trace-Id`，服务端会在响应头与响应体中回传；非法值会被替换。错误响应使用真实 HTTP 状态码，并在 `code` 中提供稳定业务错误码。

```powershell
cd backend
mvn test
mvn spring-boot:run
```

探针接口：`GET /api/system/status`。OpenAPI JSON 为 `/v3/api-docs`，非生产环境可通过 `/swagger-ui.html` 查看。

## 环境与数据库连接池

| Profile | 用途 | 数据库 | OpenAPI UI |
|---|---|---|---|
| `local` | 本机开发，默认 | MySQL + ClickHouse | 开启 |
| `dev` | 共享开发环境 | MySQL + ClickHouse，默认要求 TLS | 开启 |
| `test` | 单元/集成测试 | 不连外部数据库 | 只开启 JSON |
| `prod` | 生产 | MySQL `VERIFY_IDENTITY` + ClickHouse HTTPS | 默认关闭 |

复制 `.env.example` 中的变量到本机或部署平台的密钥管理中。Spring Boot 不会自动读取 `.env`，启动前必须将其注入进程环境。应用账号不得复用 `root`、`default` 或迁移账号。

- `mysqlDataSource` / `mysqlJdbcTemplate`：主事务数据源。
- `clickHouseDataSource` / `clickHouseJdbcTemplate`：只读分析数据源，设置 30 秒查询超时。
- `clickHouseWriterDataSource` / `clickHouseWriterJdbcTemplate`：导入 Worker 专用写入池，使用独立最小权限账号。
- 三个 Hikari 连接池独立限流，生产值通过环境变量覆盖，应小于数据库实际连接上限。

## 事务与乐观锁规范

- 事务边界放在 `ServiceImpl` 公开方法，使用 `@MySqlTransactional`；查询使用 `@MySqlTransactional(readOnly = true)`。
- 禁止在事务内执行 ClickHouse、HTTP、LLM 或对象存储等长耗时 I/O。MySQL 与 ClickHouse 不做分布式事务，跨库状态使用 Outbox/任务状态机最终一致。
- 更新必须同时限定 `tenant_id`、业务主键和 `lock_version`，并在 SQL 中 `SET lock_version = lock_version + 1`。
- 通过 `OptimisticLockExecutor.updateExactlyOne(...)` 执行；0 行转换为 `OPTIMISTIC_LOCK_CONFLICT`，超过 1 行视为系统错误。

## API 幂等

对需要幂等的 POST/PUT/PATCH Controller 方法标记 `@Idempotent`。客户端必须发送 `Idempotency-Key`、`X-Tenant-Id`、`X-Member-Id`；身份模块接入后，租户和成员值必须由服务端会话验证，不能信任请求头本身。

- 幂等作用域：`tenant + member + HTTP method + route + key`。
- 相同键和相同请求返回首次结果；同键异参返回 409；并发处理中返回 409。
- 生产使用 `sys_idempotency_request`持久化；`test` profile 使用内存实现以避免 CI 依赖外部服务。
- HTTP 幂等是防重放层，金额、配额、支付回调等关键副作仍必须使用数据库唯一键和领域幂等约束。

## 验证

```powershell
cd backend
mvn verify
```

`verify` 会依次执行 Checkstyle、编译、单元测试、API 契约测试并生成可运行 JAR。

## 数据源管理

数据源接口统一要求 `X-Tenant-Id`，并只返回 `hasCredential`，不会返回用户名、密码或 `credential_ref`：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET/POST` | `/api/datasources` | 列表 / 新增 |
| `PUT` | `/api/datasources/{id}` | 编辑，使用 `lockVersion` 乐观锁 |
| `POST` | `/api/datasources/{id}/deactivate` | 停用并撤销凭据 |
| `POST` | `/api/datasources/connection-tests` | 保存前连接测试 |
| `POST` | `/api/datasources/{id}/connection-test` | 使用已保存凭据测试 |
| `POST` | `/api/datasources/{id}/metadata/collect` | 采集 Schema、表、字段、主键和注释 |
| `GET` | `/api/datasources/{id}/metadata/snapshots` | 查看元数据版本 |
| `GET` | `/api/datasources/{id}/metadata/diff` | 对比 `fromVersion` 与 `toVersion` |

MySQL 和 PostgreSQL 连接器都强制 JDBC 只读事务，并检查源账号是否含写权限。连接前和连接后会分别解析 DNS，阻止 rebinding；默认拒绝回环、链路本地、云元数据、保留地址和内网地址，并仅允许 3306/5432。确需内网访问时，必须先在网络侧建立专线或只出站 Agent，再显式设置 `DATASOURCE_ALLOW_PRIVATE_NETWORK=true`。

凭据由 `CredentialStore` 管理，数据源表只保存 opaque `secret://` 引用。当前内置实现使用 AES-256-GCM 加密并支持撤销；生产必须通过 `DATASOURCE_MASTER_KEY` 注入 Base64 编码的 32 字节主密钥，或将 `CredentialStore` 替换为企业 KMS/Vault 适配器。日志和 API 均不输出源凭据。

## 文件导入、数据同步与质量

文件导入支持 CSV/XLSX 上传、安全解析、预览推断、严格/宽松导入及异常行报告。导入在独立有界执行池运行，默认 2 个 Worker、50 个排队位，队列满返回 429。默认单文件上限 100 MB，XLSX 禁止公式、宏、嵌入对象和外部链接，并开启 ZIP 解压比例防护。

| 能力 | 主要路径 |
|---|---|
| 文件上传、预览、下载 | `POST /api/files/uploads`、`GET /api/files/{id}/preview`、`GET /api/files/{id}/content` |
| 导入任务 | `POST/GET /api/imports`、`GET /api/imports/{id}`、`POST /api/imports/{id}/cancel` |
| 同步任务 | `POST/GET /api/sync-jobs`、`PATCH /api/sync-jobs/{id}`、`enable`、`pause`、`run` |
| 同步运行 | `GET /api/sync-runs`、`GET /api/sync-runs/{id}`、`cancel`、`compensate` |
| 质量规则 | `POST/GET /api/quality-rules`、`POST /api/quality-runs/{batchId}` |

全量同步使用新 `generation_id`，增量同步使用 `(updated_at, primary_key)` 组合水位。行事件幂等域包含租户、数据集、数据源、代、发布序号、业务键和源版本。质量通过后，发布指针 CAS、批次 `PUBLISHED` 和水位推进在同一 MySQL 事务中提交。
