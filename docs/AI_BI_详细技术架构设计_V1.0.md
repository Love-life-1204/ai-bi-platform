# 商业化 AI BI 智能数据分析平台

## 详细技术架构设计 V1.0

**文档性质：** 企业级技术设计评审稿  
**编制日期：** 2026-10-09  
**技术基线：** Vue 3 + Spring Boot + Python FastAPI + LangGraph + MySQL + ClickHouse  
**适用团队：** 架构、前端、Java、Python、数据工程、测试、运维、安全、实施  
**上游文档：** 《商业化 AI BI 智能数据分析平台—完整系统需求规格说明书 V1.0》（2026-10-09）

> 文档中的 P0/P1/P2、FR/NFR、默认超时、配额、数据口径和发布门槛继承需求基线。具体基础组件、表结构、通信契约及执行策略为本技术设计提出的**实施建议**，未经过工程验证前不属于已经完成的功能或商业 SLA。

## 1. 架构目标与边界

### 1.1 设计目标

1. **多租户强隔离：** 同一平台支持多企业、多工作空间、多角色；任意图表、查询、AI 检索、缓存、文件与异步任务不能跨越有效授权范围。
2. **正确优先：** 同步批次发布、语义层指标版本、货币精度与行列权限构成 BI 可信度基础。所有事实须具备数据批次、质量状态及指标版本。
3. **AI 可控：** 大模型负责理解、计划和解释；Java 执行最终授权与 SQL 编译。不存在“大模型直接连库”或“先查出明细再让模型过滤”的路径。
4. **渐进演进：** P0 优先选择模块化单体与独立 AI / Worker，避免先拆十余个微服务。将易扩容的数据处理、AI 和查询执行做成独立运行资源池。
5. **可恢复、可运维、可对账：** 不使用 MySQL 和 ClickHouse 跨库事务；采用批次状态机、Outbox、幂等处理、读侧版本闸门、审计和补偿任务。

### 1.2 与需求基线的对应关系

| 设计边界 | 需求编号 | 落地位置 |
|---|---|---|
| 租户、成员、工作空间 | FR TEN 001/002 | 第 5、6、16 章 |
| 登录与数据权限 | FR IAM 001/002 | 第 5、6、10 章 |
| 数据源和元数据 | FR SRC 001/002 | 第 7 章 |
| 导入、同步、数据质量 | FR ING 001/002/003 | 第 7、8 章 |
| 数据集、术语、指标 | FR SET 001/002、FR MET 001/002 | 第 9 章 |
| 受控查询 | FR QRY 001/002 | 第 10 章 |
| 图表与看板 | FR VIS 001/002/003 | 第 11 章 |
| AI 工作流与问答 | FR AI 001—006 | 第 12、13 章 |
| 报表、告警、计费 | FR REP、FR ALT、FR BIL | 第 14、15 章 |
| 审计、恢复、性能与测试 | FR AUD/OPS、NFR SEC/PERF/AVA/OBS | 第 16—21 章 |

### 1.3 不纳入 P0 的能力

不开发 ERP 交易写入、任意自由 SQL 执行、未经审查的跨源实时联邦查询、公开匿名看板、自动经营决策、无条件因果归因。P1 扩展 OIDC/MFA、订阅与付费、报表订阅和外部集成；P2 扩展预测和高级异常分析。

## 2. 架构决策记录（ADR）

| ADR | 决策 | 选择理由 | 代价与演进 |
|---|---|---|---|
| ADR-01 | Spring Boot 模块化单体，单独部署 FastAPI 和 Worker | 统一事务、权限与语义口径，降低早期分布式复杂度 | 后续依边界抽离查询/计费服务 |
| ADR-02 | MySQL 管事务配置，ClickHouse 管分析数据 | OLTP/OLAP 各司其职 | 需异步一致性、快照指针和补偿 |
| ADR-03 | 默认抽取同步至 ClickHouse，不直接跨源联邦 | 统一查询权限、性能和指标定义 | 数据新鲜度受同步策略约束 |
| ADR-04 | 查询仅接受类型化 DSL/AST 白名单 | 避免 LLM SQL 直接执行、避免过滤绕过 | P0 SQL 能力有明确子集 |
| ADR-05 | 平台共享资源池 + tenant_id + workspace_id + 数据策略 | 中小企业 SaaS 成本可控 | 大客户可独立实例/集群，逻辑鉴权不移除 |
| ADR-06 | MySQL Outbox + RabbitMQ 至少一次投递 + 幂等消费 | 避免消息与业务库双写不一致 | 需要去重、重试和死信治理 |
| ADR-07 | Redis 分离缓存实例与 LangGraph 持久化实例 | 复用成熟组件，checkpoint 避免进程内存丢失 | 持久化 Redis 需 AOF、备份、非淘汰策略；严格场景可换专用 DB saver |
| ADR-08 | 数据版本按“完整代 generation + 连续已发布序号”选择 | 隐藏尚未校验数据，支持增量与全量切换 | 查询须执行版本条件和业务键逻辑去重 |
| ADR-09 | 金额按最小货币单位/Decimal 存算，前端用字符串传输 | 避免 JavaScript 精度丢失和金额误差 | 指标需定义币种、精度和退款口径 |
| ADR-10 | Kubernetes 作为商用环境推荐部署形态 | 便于隔离、扩容、灰度和资源预算 | 开发可用 Docker Compose，发布门槛仍需 HA 演练 |

**取舍说明：** LangGraph 官方文档列出 Redis、PostgreSQL 等持久化 checkpointer；此方案优先选独立的、支持持久化的 Redis checkpointer，避免引入第三种关系数据库。产品冻结时应进行兼容性与故障恢复验证；如环境约束不满足，改用经验证的 PostgreSQL checkpointer 是允许的 ADR 变更，但不能退化为生产 `InMemorySaver`。

## 3. 总体逻辑、部署与信任拓扑

### 3.1 六层划分

- **体验层：** Vue3 SPA，PC 完整功能，移动端只读看板与问答；浏览器不持有客户数据库凭据。
- **边缘层：** TLS 入口、WAF/限流、反向代理、静态资源、请求追踪标识；向公网只暴露经审批的入口。
- **业务与控制层：** Spring Boot 负责身份、租户、数据资产、指标、查询、看板、配额、审计与 API 聚合。
- **AI 计算层：** FastAPI + LangGraph，调用受控工具，进行意图识别、语义检索、分析计划与证据回答；仅内网可访问。
- **数据处理层：** Connector Worker、Importer、Scheduler、Quality Validator、Exporter；工作进程不得共享超级管理员凭据。
- **存储与基础设施层：** MySQL、ClickHouse、Redis、RabbitMQ、对象存储、密钥管理、监控和备份。

### 3.2 建议物理拓扑

```text
互联网 / 企业浏览器
     │ HTTPS 443
     ▼
WAF / Load Balancer / Ingress
     ├──── 静态站点 Vue3（CDN 或 Nginx）
     └──── /api/v1/*  ──► Spring Boot Java API ×2+
                               │        ├── MySQL 主备（事务）
                               │        ├── Redis Cache（缓存/限流）
                               │        ├── RabbitMQ（任务事件）
                               │        ├── Query Executor ──► ClickHouse
                               │        └── 内网签名调用 ──► FastAPI ×2+
                               │                               ├── LangGraph
                               │                               ├── 模型供应商网关
                               │                               └── Redis Checkpoint（持久化）
                               └── Worker 池 ──► 数据源（只读）
                                      ├── CSV/XLSX ──► S3 对象存储
                                      ├── 批次校验 ──► ClickHouse
                                      └── 回填/补偿/导出
全链路：KMS/Vault + OpenTelemetry + Prometheus/Grafana + 集中日志 + 备份
```

### 3.3 禁止的通信路径

浏览器不得直接访问 FastAPI、ClickHouse 或外部企业数据源；FastAPI 不允许绕过 Java QueryService 直接执行数据库查询；模型供应商看不到平台密钥、租户令牌和超范围数据；平台运营身份默认不可读取租户原始事实；Connector Worker 不得将客户网络作为任意代理。

### 3.4 关键交互序列

**用户 AI 问答：** 浏览器 POST `/api/v1/ai/conversations/{id}/messages` → Java 校验成员和配额、创建任务和业务流水 → 生成短时签名上下文 → 内部 FastAPI 开始 Graph → 语义检索（经 Java 授权快照）→ Graph 输出 DSL → Java QueryCompiler 验证权限与版本、执行 ClickHouse → 仅返回被允许的聚合/脱敏结果 → Graph 生成事实和解读 → Java 交付前再次鉴权 → SSE 返回并落审计/结算。

**数据同步：** 定时器写 MySQL 任务/Outbox → 消息投递 → Worker 领取租约 → 只读抽取 → 写 ClickHouse 未发布批次 → 数据质量/对账 → 通过 MySQL CAS 发布指针 → 缓存按版本自然切换并发通知 → 完成审计。失败时保持旧指针可读。

## 4. 工程组织、服务与模块边界

### 4.1 推荐单仓库初始目录

```text
ai-bi-platform/
├─ frontend/                 # Vue3 企业端、平台运营端
│  ├─ src/api/  src/router/  src/stores/
│  ├─ src/modules/            # tenant iam source dataset metric dashboard ai ...
│  └─ src/components/         # 图表、权限组件与公共状态
├─ backend-java/
│  ├─ bi-api/                 # Spring Boot 入口、Controller
│  ├─ bi-common/              # 统一错误、上下文、审计、API DTO
│  ├─ bi-iam/  bi-tenant/  bi-saas/
│  ├─ bi-source/  bi-ingestion/  bi-semantic/
│  ├─ bi-query/  bi-dashboard/  bi-report/
│  ├─ bi-ai-bridge/  bi-ops/
│  └─ bi-worker/              # 独立启动，重用业务模块
├─ ai-service/
│  ├─ app/api/  app/graphs/  app/nodes/
│  ├─ app/tools/  app/models/  app/schemas/
│  ├─ app/security/  app/evaluations/  tests/
├─ infrastructure/
│  ├─ docker/  helm/  k8s/  monitoring/  migrations/
├─ contracts/                # OpenAPI/JSON Schema/事件 schema
├─ docs/                     # ADR、设计、runbook、威胁模型
└─ tests/                    # 端到端、租户隔离、并发与混合压测
```

### 4.2 Spring Boot 分层与约束

采用 `Controller → Service（接口）→ ServiceImpl → Manager → Mapper/Repository → Entity`，通过 DTO、领域对象隔离接口与持久化模型。Controller 只做协议校验和入参映射；Service 管事务及用例；Manager 管查询编译、幂等、对外适配；Mapper 只负责持久化。禁止 Controller 直接访问 Mapper 或由 AI 直接访问数据库 Mapper。跨模块通过 Service 接口与领域事件调用，避免循环依赖。

典型类：`DatasetController`、`DatasetService`、`DatasetServiceImpl`、`DatasetPublishManager`、`DatasetVersionEntity`、`DatasetVersionMapper`。每个公共 Service 都以 `TenantExecutionContext` 为强制参数或通过可信服务端作用域读取，禁止仅依赖客户端 `tenant_id`。

### 4.3 模块清单与契约

| Java 模块 | 核心职责 | 对外/内部接口 |
|---|---|---|
| tenant | 企业生命周期、部门、成员、工作空间 | TenantService / MembershipService |
| iam | 登录、会话撤销、RBAC/ABAC、行列策略 | AuthenticationService / PolicyDecisionService |
| source | 连接管理、凭据引用、元数据快照 | DatasourceService / ConnectorRegistry |
| ingestion | 同步任务、导入、批次与质量 | SyncService / BatchPublisher / QualityService |
| semantic | 数据集、关联、指标、版本及术语 | DatasetService / MetricService / SemanticCatalog |
| query | Query DSL、权限编译、执行、缓存、审计 | QueryPlanCompiler / QueryExecutor / QueryService |
| dashboard | 图表规范、看板版本、共享与下钻 | ChartService / DashboardService |
| ai-bridge | AI 任务、签名上下文、流式代理、用量 | AiConversationService / AiTaskGateway |
| report | 报告模板、导出任务和权限交付 | ExportService / ReportDeliveryService |
| saas | 套餐、配额、订单、支付回调和对账 | QuotaService / SubscriptionService |
| ops | 运营元数据、审计、特权支持和租户退出 | AuditService / TenantExitService |

### 4.4 代码质量规范

所有 Java/Python/TypeScript 关键逻辑添加简明中文注释；先抽象可复用服务，再实现业务流程；禁止无必要对象深拷贝、深层嵌套和隐式共享可变状态；单元测试和跨模块契约测试随功能提交；影响同步、查询编译、计费的重构坚持小步变更并运行回归。使用依赖锁定、统一格式化、静态检查及 Code Owners 审核。

## 5. 多租户 SaaS 隔离与授权数据流

### 5.1 隔离维度

| 资源 | P0 方案 | 强制检查点 |
|---|---|---|
| MySQL | 共享数据库 + `tenant_id` + 复合索引/约束 | Mapper 查询注入可信 tenant 范围，Service 再验证归属 |
| ClickHouse | 共享事实表，必含 `tenant_id` 与数据版本 | Java AST 编译阶段注入租户和行列策略；查询账号仅有允许的表 |
| Redis Cache | `env:tenant:subject:permissionVersion:...` | 限权后的规范化 key；撤销权限版本增加 |
| AI State | tenant + conversation + thread 的稳定 namespace | Java 验签，Python 校验 tenant/thread 所属；恢复前重鉴权 |
| 对象存储 | tenant namespace + 唯一文件 ID + 密钥策略 | 下载每次先经 Java 鉴权代理，不能直发不可撤销长效 URL |
| RabbitMQ/Worker | payload 不可信；任务只含 ID 与最小上下文 | 消费者重新读取 MySQL 租户、状态与权限 |
| 审计/日志/索引 | tenant 分区标签与访问控制 | 平台仅访问脱敏运营统计，用户按租户过滤 |

### 5.2 身份与租户上下文

全局账户 `sys_user` 可以是多个租户的成员；`sys_tenant_member` 记录当前企业中的成员 ID、状态与角色；访问请求选择 `tenant_id` 后由后端根据会话确定 `member_id`、`workspace_id`、`permission_version`。前端请求头 `X-Tenant-Id` 只是目标租户选择，不是授权证据。JWT/Session 中 subject 与 member 映射经过服务端校验；敏感操作重新认证。

租户切换时前端重建会话状态、关闭旧 SSE、取消查询、清空缓存，并申请绑定新 tenant 的令牌或会话上下文。后端每次处理仍校验租户成员有效性；权限版本变更后旧结果、异步任务和下载链接不得继续交付。

### 5.3 权限模型

最终允许条件：`activeTenant ∧ activeMember ∧ resourceBelongsToTenant ∧ permission(action, resource) ∧ workspaceScope ∧ rowPolicy ∧ columnPolicy ∧ quota`。角色权限取并集但显式拒绝优先；行级同组 OR、跨组 AND；默认无数据策略即拒绝；列级禁止字段不得用于 SELECT、过滤、分组、排序、AI 提示词。策略用受约束 DSL 例如 `region_id IN allowed_region_ids`，不接收任意 SQL 字符串。

### 5.4 授权撤销与缓存一致性

策略或成员变更：MySQL 更新并递增 `permission_version` → 同事务 Outbox → 通知缓存驱逐 → 请求重新加载版本。安全关键操作查询数据库或可靠的强一致版本来源；不得仅等待 TTL。已排队任务在领取、执行、结果交付三个阶段鉴权，执行中变更则发取消信号；查询执行前、回传前复核。验收以需求指定的 60 秒缓存失效上限及新请求即时拒绝为准。

### 5.5 企业高级隔离模式

默认共享基础设施与强逻辑隔离；企业客户可选独立 ClickHouse database/集群、独立对象存储账户/桶、独立 Worker 池、独立 KMS key 与 VPC。资源位置由 `TenantResourceLocator` 注册表解析；不能仅切换租户标签来声称物理隔离。数据库路由配置只由平台可信控制面决定。

## 6. 身份认证、会话与策略引擎

### 6.1 登录与凭据

Spring Security 实现账号密码、邀请、密码重置和刷新令牌轮换；密码采用 Argon2id 或经过评审的安全哈希，刷新令牌仅保存哈希/会话指纹，支持撤销与设备级登出。建议访问令牌 15 分钟、刷新会话 7 天；P1 增加 TOTP/WebAuthn MFA 和 OIDC SSO。风控、限速、错误信息统一，避免暴露用户是否存在。

### 6.2 策略决策接口

`PolicyDecisionService.authorize(subject, action, asset, context)` 返回 `ALLOW/DENY`、批准行过滤 AST、列访问映射、策略版本、有效期和 `decision_id`。数据集预览、明细查询、指标聚合、看板、导出与 AI 共享同一决策逻辑，禁止每个 Controller 写一套相互矛盾的权限规则。

```json
{
  "decision_id": "dec_001",
  "allowed": true,
  "tenant_id": "t_1001",
  "member_id": "m_901",
  "permission_version": 28,
  "row_predicate": {"field_id": "f_region", "op": "IN", "values": ["EAST"]},
  "columns": {"f_amount": "VISIBLE", "f_mobile": "DENY"}
}
```

该例仅描述后端生成的策略结构，不能允许前端或大模型自行构造 `row_predicate` 作为可信输入。

### 6.3 安全操作

创建数据源凭据、导出敏感数据、角色升级、数据退出、账单退款、设置模型数据外发策略等要求二次认证、明确确认和完整审计。平台支持人员需租户发放工单级临时权限，默认最长 2 小时，结束即撤回。

## 7. 数据源、文件导入与异步同步

### 7.1 连接器抽象与执行边界

P0 数据源：MySQL、PostgreSQL 只读数据库和 CSV/XLSX；P1 外部 ClickHouse、CDC、客户侧 Agent。`Connector` 统一实现 `validateConfig()`、`testConnection()`、`fetchMetadata()`、`scanPage()`、`close()`，数据库方言由 Adapter 提供稳定主键、增量时间字段、类型映射、限流及快照读能力。

连接配置只保存 host、port、DB、TLS、网络位置与 `credential_ref`；密码和 Token 在 KMS/Vault 受控边界保存。Worker 使用单任务短时凭据，从未授权目的地址访问源端一律拒绝。DNS 解析结果与重定向每跳均检查出站白名单及云元数据地址，禁止跨网络探测。企业内网优先客户侧仅出站 Agent 或明确批准的网络专线。

### 7.2 元数据采集

元数据快照包含 datasource、schema、table、column、稳定字段 ID、类型、主键、注释、采集时间及 schema_hash。仅采集白名单对象，不默认复制明细。源端注释、表名和示例数据均为不可信信息，不得自动拼接为大模型系统指令。检测字段删除、类型改变、关联基数变化，生成差异工单；影响的发布语义模型标记 `NEEDS_REPAIR` 并暂停相关新查询。

### 7.3 文件导入执行链

```text
创建上传会话 → 获取短时受控上传地址 → 上传至隔离对象空间
→ 病毒/内容/压缩膨胀/扩展名与魔数校验 → CSV/XLSX 安全解析
→ 字段及类型预览 → 用户确认映射 → 生成 import_job
→ 分块写入未发布批次 → 错行隔离 → 质量及数量对账
→ 发布 batch → 展示数据质量/错误文件/快照
```

默认文件 100MB、单批 100 万行；文件导入允许将订单编码、身份证号等强制为字符串。禁止执行 Excel 宏/公式；导出 CSV 时对以 `=,+,-,@` 等开头的潜在公式内容做安全编码，并保留原值在业务库中的语义。严格模式出现错误则整批阻断；宽松模式只发布校验通过的行，公开显示被拒行数。

### 7.4 增量同步算法

优先使用 `updated_at + stable_primary_key` 的复合水位。排序 `(updated_at ASC, primary_key ASC)`；分页查询条件为 `(updated_at > lastTime) OR (updated_at = lastTime AND pk > lastPk)`，并设置回看窗口覆盖延迟更新。水位仅在目标写入、数量/金额对账、质量检查和批次发布都成功后推进。源端不存在可用更新时间或主键时不假装增量可靠，改用分段全量快照或客户认可的方案。

P1 CDC 单独处理 DDL、事务边界、offset、重排、删除事件与断点恢复。没有删除事件来源时明确标记“不能同步源端硬删除”，通过软删字段或周期全量对账修复。

### 7.5 状态机和消息处理

| 任务状态 | 进入条件 | 允许的后续状态 |
|---|---|---|
| PENDING | 参数校验通过，任务与 Outbox 已落库 | RUNNING、CANCELLED |
| RUNNING | Worker CAS 抢占任务租约且取得 fencing token | SUCCESS、PARTIAL_SUCCESS、FAILED、CANCELLED、TIMEOUT |
| SUCCESS | 全部校验通过，发布版本成功 | 终态 |
| PARTIAL_SUCCESS | 已明确记录坏行和影响范围 | 终态；不能假称完整 |
| FAILED/TIMEOUT | 运行失败或超时，已有错误分类 | 人工重试生成新 attempt，保留原任务 |
| CANCELLED | Worker/查询引擎确认终止 | 终态 |

MySQL `job_run` 用乐观版本实现状态 CAS，`job_lease` 提供持有者、到期时间和递增 fencing token。任务写库使用 Outbox 与业务事务同提交，投递方只保证至少一次，消费者用 `(tenant_id, job_id, attempt_id, batch_id)` 幂等。可恢复错误最多 3 次指数退避，永久性错误进入死信和人工修复流程。Redis 锁可减少争用，但不能替代 MySQL 状态 CAS 与 fencing。

### 7.6 质量管理与对账

规则分阻断级和告警级：行数、非空、主键唯一、类型、范围、关联完整性、新鲜度、金额与业务键对账。执行结果包括 `expected_count`、`received_count`、`valid_count`、`rejected_count`、`deduplicated_count`、`currency_minor_unit_total`、`quality_status`。校验通过后发布；阻断级失败继续返回上一有效版本，不能产生“零销售”业务结论。

## 8. 数据库存储与版本一致性设计

### 8.1 MySQL 数据分类

| 前缀 | 代表表 | 说明 |
|---|---|---|
| sys_ | `sys_user`, `sys_tenant`, `sys_tenant_member`, `sys_role`, `sys_policy`, `sys_workspace` | 身份、租户和授权 |
| bi_ | `bi_datasource`, `bi_sync_job`, `bi_batch`, `bi_dataset_version`, `bi_metric_version`, `bi_dashboard_version` | 数据资产与任务 |
| ai_ | `ai_conversation`, `ai_message`, `ai_task`, `ai_tool_invocation`, `ai_model_usage` | AI 会话、证据与成本 |
| saas_ | `saas_plan_version`, `saas_subscription`, `saas_order`, `saas_quota_ledger` | 订阅与支付 |
| sys_/bi_ | `sys_audit_event`, `bi_outbox_event`, `bi_query_execution`, `bi_export_task` | 审计、消息和可恢复任务 |

租户级业务表带 `tenant_id`、`created_at`、`updated_at`、`created_by`、`updated_by`、`version`、软删除信息（适用时），所有租户级唯一索引须包含 `tenant_id`。关系外键可用复合键，例如 `(tenant_id, workspace_id)` → `(tenant_id, id)`，防止跨租户关联。主键建议使用不可预测 UUIDv7/雪花 ID； ID 不具备授权能力。使用 Flyway 或 Liquibase 进行版本迁移；避免线上无计划直接修改历史版本行。

### 8.2 ClickHouse 数据层

- `raw`：技术隔离的写入暂存数据，仅 Worker 和质量校验角色可见。
- `dwd`：经过结构化、类型统一、去重约束的销售事实、客户、商品、库存维度。
- `dws`：经批准的指标聚合表或物化结果，用于高频图表。
- `ads`：服务端对外发布的语义查询视图，业务用户不直接访问原生表。

事实最小字段：`tenant_id`、`dataset_id`、`source_id`、`business_key`、`immutable_partition_date`、`business_time`、`source_version`、`generation_id`、`publish_seq`、`is_deleted`、`batch_id`、`ingested_at`、金额列与业务维度列。ClickHouse 分区按稳定的业务时间或批次代设计，不对每个 tenant 单独分区。关键业务键和物理排序键应保证同一逻辑记录的不同版本能够正确比较。

### 8.3 全量 + 增量的发布协议（P0 核心）

1. **初始化：** `bi_dataset_publication` 维护 `(tenant_id, dataset_id, active_generation, committed_publish_seq, data_quality_version, updated_at, version)`。对每个数据集串行化发布，确保 `publish_seq` 连续。
2. **全量：** 创建新 `generation_id` 并将全量行写入该代、标记 `publish_seq=1`。写入成功、可查询验证、关键指标对账后，MySQL CAS 一次性把 `active_generation` 切换至新代；旧代保留回滚窗口。
3. **增量：** 在当前 generation 写新行，使用下一连续 `publish_seq`，即使 Writer 被重放，也使用相同 `(batch_id, source_version)`；全部完成后 CAS 将 `committed_publish_seq` 推进到新值。
4. **读路径：** Java 查询开始时固定读取发布指针；查询只读 `generation_id=active_generation` 且 `publish_seq<=committed_publish_seq` 的行，并**先过滤可见发布序号，再按业务键选择最新版本**。未发布批次永远不能影响既有结果。
5. **失败与补偿：** 指针在写入前不可推进。若数据写完但发布事务失败，重试同一批次；若 MySQL 已发布但读侧副本尚不可见，则暂停新版本交付并执行健康/一致性补偿，不得把不完整结果当成功返回。

**重要准确性约束：** `ReplacingMergeTree` 只能作为存储组织策略，不能把后台 merge 当作即时去重保证。含新旧修订的指标查询需要在可见版本集合内使用有界 `argMax` 聚合或经验证的 `FINAL` 语义。若直接先 `FINAL` 再过滤未发布行，可能使旧的已发布版本丢失。本方案规定：“**批次可见性过滤先于最终业务键去重**”。所有排序/去重必须以租户 + 源 + 业务键为界，版本比较值要单调且消除平局。

### 8.4 示例 ClickHouse 事实表（概念 DDL，正式发布需适配真实数据）

```sql
CREATE TABLE dwd_sales_order
(
    tenant_id String,
    dataset_id String,
    source_id String,
    order_id String,
    immutable_created_month Date,
    order_time DateTime64(3, 'UTC'),
    region_id String,
    net_amount_cents Decimal(20, 0),
    source_version UInt64,
    generation_id String,
    publish_seq UInt64,
    is_deleted UInt8,
    batch_id String,
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(immutable_created_month)
ORDER BY (tenant_id, dataset_id, source_id, order_id, source_version, publish_seq);
```

此例使用不可变明细加业务键逻辑版本选择，不以引擎自动去重作为正确性承诺。`Decimal(20,0)` 存最小货币单位，平台按币种/字段元数据解释精度。对大表需根据真实查询谓词、压缩、数据分布和 Part 数量进行排序键与分区基准测试。生产环境可根据已验证方案采用 ReplacingMergeTree、投影或聚合物化视图，但须保持相同逻辑正确性。

### 8.5 带发布闸门的查询模板（示意）

```sql
WITH latest_orders AS
(
    SELECT tenant_id, source_id, order_id,
           argMax(tuple(region_id, net_amount_cents, is_deleted),
                  tuple(source_version, publish_seq)) AS row_data
    FROM dwd_sales_order
    WHERE tenant_id = {tenant_id:String}
      AND dataset_id = {dataset_id:String}
      AND generation_id = {generation_id:String}
      AND publish_seq <= {committed_seq:UInt64}
    GROUP BY tenant_id, source_id, order_id
)
SELECT tupleElement(row_data, 1) AS region_id,
       sum(tupleElement(row_data, 2)) AS amount_cents
FROM latest_orders
WHERE tupleElement(row_data, 3) = 0
  /* 此处还须在内层数据访问及授权语义上注入可信行级策略 */
GROUP BY region_id;
```

该模板用 ClickHouse 原生带类型参数占位符说明版本选择，不能直接作为生产 SQL 编译器。生产 SQL 需要在选择事实行前以可信行级策略限制相应字段与关联范围，并处理字段变更、时间口径、NULL、权限过滤与维度历史版本；禁止仅在外层聚合结果之后附加 RLS。

### 8.6 财务指标与时区

金额用 Java `BigDecimal` 和 ClickHouse Decimal/最小货币单位，REST/JSON 用十进制字符串返回金额。时间统一保存 UTC，按 tenant 配置时区解释业务时间，查询采用 `[start, end)`。订单支付、取消、退款计入期间以指标版本定义，不由查询页面临时臆断；未定义汇率来源的多币种数据禁止直接合计。

## 9. 语义层、数据集和指标引擎

### 9.1 三层元数据模型

1. **物理层：** datasource、source_table、source_column、physical_type、schema_snapshot、source_primary_key。
2. **业务数据集层：** dataset_version、field_id、field_mapping、join_contract、field_classification、sensitivity、row_policy_ref。
3. **指标与知识层：** metric_version、expression_ast、aggregation、time_rule、currency_rule、allowed_dimensions、synonyms、approved_query_examples、glossary_version。

面向产品用户使用“订单、客户、商品、销售额、转化率”等业务对象，不暴露底层可执行 SQL。每个指标必须引用冻结的数据集版本及依赖指标版本，DAG 中检测环并拒绝发布。

### 9.2 数据集发布流程

`DRAFT → VALIDATING → VALIDATED → PUBLISHED → ARCHIVED`。验证包括字段存在、类型兼容、引用有效、敏感列、行级策略、关联基数、多对多风险、公式语法和指标依赖；发布时固化 JSON 规范及哈希，保存 `published_by`、`published_at`、`parent_version_id`。已发布版本只可新增后继，不覆盖修改。下游图表和 AI 固定引用发布版本或显式执行兼容升级。

### 9.3 关联防重复

每个 Join 必须声明 `(leftKey, rightKey, cardinality, temporalRule, permissionStrategy)`。右侧声明一对一时要求业务键唯一；发现重复键阻断或提交明确去重策略。历史维度要求 SCD Type 2 的 `[valid_from, valid_to)` 与事实时间关联，不允许用最新商品类目默默重写历史报告口径。

### 9.4 指标表达式 DSL

允许聚合 `SUM`、`COUNT`、`COUNT_DISTINCT`、`MIN`、`MAX`、`AVG`，数学 `ADD/SUB/DIV`，条件 `CASE`、时间 `DATE_TRUNC` 和受控同比环比；函数名单和参数类型由 `MetricExpressionValidator` 控制。净销售额可编译为“有效支付订单总额减已确认退款额”，计算口径必须附明确定义。利润率计算为 `sum(profit) / nullif(sum(revenue), 0)`，不能平均各订单利润率。日期缺失、零基期返回 `undefined` 状态而不是虚构 0% 或 ∞。

### 9.5 语义搜索与索引

P0 使用 MySQL 元数据字段检索、业务术语关键词及人工批准模板；通过 `(tenant_id, workspace_id, permission_version, semantic_version)` 限定召回。P1 可以引入专用向量索引，但仅索引已批准的元数据/脱敏描述，索引撤销和重建须与权限版本联动。大模型输出字段名必须映射稳定 field_id，再由服务端做版本/权限检查。

## 10. 受控查询编译器与执行服务

### 10.1 查询契约：强类型 QueryPlan

```json
{
  "dataset_version_id": "dsv_12",
  "metric_version_ids": ["mv_net_sales_4"],
  "dimension_field_ids": ["f_region"],
  "time_range": {"field_id": "f_paid_at", "start": "2026-09-01T00:00:00+08:00", "end": "2026-10-01T00:00:00+08:00"},
  "filters": [{"field_id": "f_status", "operator": "EQ", "value": "PAID"}],
  "sort": [{"field_id": "mv_net_sales_4", "direction": "DESC"}],
  "limit": 100
}
```

该计划来自浏览器或 LLM 时一律视为**候选请求**，没有可信 tenant、角色或附加权限字段。Java 依据认证上下文重新补全租户、成员、授权策略、被引用版本和数据发布指针，并规范化为 `AuthorizedQueryPlan`；原始 SQL 仅是禁用或严格受限的高级路径。

### 10.2 编译步骤

1. JSON Schema/类型校验、复杂度与长度限制；拒绝未知字段和不支持运算符。
2. 解析 dataset/metric/field ID，载入确切发布版本、依赖图与数据发布指针。
3. 解析 `PolicyDecision`，按每个事实扫描、维度关联与子查询位置注入 tenant/RLS，并检测被禁列在筛选、排序或派生表达式中的间接使用。
4. 检查 Join 基数、可用维度、时间字段、币种、NULL、TopN、粒度一致性及估算成本。
5. 生成受控内部 AST → 经白名单方言编译为单一只读 SQL 和类型化参数；禁止动态表名、任意函数、多语句、系统表、网络/文件访问。
6. 限定查询超时、最大扫描量、最大结果行数、租户并发预算、ClickHouse 账号与 query_id。
7. 执行、校验响应字段/金额精度、记录数据版本与质量、缓存并审计；完成前二次鉴权。

### 10.3 执行策略与 SQL 支持子集

P0 支持单事实数据集、批准的一对一/多对一 Join、GROUP BY、受控 SUM/COUNT/AVG、时间窗口、受控 TopN、排序、分页、同比环比的明确编译模板。P0 拒绝任意 UNION、任意 CTE、递归、自由窗口函数、跨数据源 Join、系统表、ClickHouse table function、外部 URL 文件函数、动态 DDL/DML 和未经批准的 UDF。对用户自写 SQL 的 P1 沙箱模式必须经过完整解析、权限重写、只读账户与独立压测后才能启用。

### 10.4 资源控制与公平性

默认普通查询 30 秒、返回最多 10,000 行；单租户最多 5 个活动查询和 20 个等待任务；超限时明确拒绝或排队而非阻塞无界队列。多级信号量按 tenant、工作空间、用户、全局 Query Pool 控制，使用令牌桶/公平调度避免大客户挤占其他租户。将导出任务与交互查询部署到不同线程池、队列、ClickHouse 用户 profile；用户取消后使用查询 `query_id` 在服务端执行数据库级终止。

### 10.5 缓存键与权限变更

`cache_key = hash(tenant_id | member_or_permission_fingerprint | permission_version | dataset_version | metric_versions | generation | committed_seq | query_plan_hash | timezone | data_policy_hash)`；只缓存已完成、质量状态允许的查询结果。相同 SQL 不等于相同权限，禁止跨租户共用结果。若权限版本变更或租户切换，立即禁止交付旧缓存；对大数据采用指标级聚合缓存或版本化物化结果，避免保存大规模明细。

### 10.6 QueryExecution 记录

记录 `query_id`、`tenant_id`、`member_id`、`permission_version`、`dataset_version_id`、`metric_version_ids`、`publication_snapshot`、`plan_hash`、`trace_id`、`execution_ms`、`row_count`、`error_code`、`cancel_status`。保存受控 SQL 摘要与必要参数哈希，默认不记录敏感明细或完整 Token。

## 11. Vue3 前端架构及看板实现

### 11.1 页面模块

工作台、组织成员与权限、数据源、同步批次、文件导入、数据集建模、指标管理、图表设计、看板编辑与查看、AI 对话、报告、告警、账单/套餐、审计与平台控制台。按业务域 `src/modules/*` 懒加载，公共 `api-client` 实现 trace_id、统一错误、令牌刷新与失败分类。

### 11.2 状态管理与安全

Pinia 持有认证状态、当前 tenant/workspace、菜单能力、视图状态与轻量缓存；不持久化数据源凭据、敏感明细或模型调用密钥。切换租户必须取消正在进行的请求与 SSE，移除旧工作空间缓存，导航至新租户的权限验证入口。前端隐藏功能只属于体验层，真正授权总在后端。

### 11.3 看板 Schema

图表包含 `chart_id`、`dataset_version_id`、指标/维度版本、筛选、图表类型、格式、坐标轴、空值规则和联动事件；看板版本包含二维 Grid 布局、全局筛选、页面顺序、组件引用、刷新策略、发布状态。采用 JSON Schema 验证配置，ECharts 仅接收安全图表规格与服务端数据，不执行模型返回的 JavaScript/HTML。保存采用乐观锁 `If-Match/version`，发布生成不可变版本。

### 11.4 首屏性能、并发与退化

Vite 按路由/图表动态切包，网格组件虚拟化或按可视区域请求，合并同源查询并施加并发上限。默认自动刷新不短于 60 秒，后台标签页暂停；单组件失败只显示错误卡，不引发整体白屏。不同图表必须复用同一业务时间规则与数据快照；如不同数据源新鲜度不一致，显式显示来源和刷新时间。桌面优先，移动端 P0 仅提供只读看板和基础 AI 查询。

## 12. FastAPI 与 LangGraph 智能数据分析架构

### 12.1 AI 服务定位和责任分离

FastAPI 仅接收内网服务认证过的 `AiTaskContext`；负责模型供应商路由、LangGraph 执行、工具契约、事实解释和流式进度。Java 是任务所有者、最终授权源、计费原账本和所有用户可见数据的交付网关。AI 服务不允许使用 JDBC/ClickHouse 高权限账号，不允许以 `tenant_id`、`role` 等模型文本覆盖受信任务上下文。

### 12.2 工作流节点明细

| 节点 | 输入 | 核心处理 | 输出与失败策略 |
|---|---|---|---|
| 01 `request_guard` | 签名上下文、问题、任务 ID | 验签、过期检查、配额状态、数据外发策略 | 拒绝不可信/过期任务 |
| 02 `intent_classifier` | 问题、已确认会话状态 | 判断查询/解释/帮助/非 BI 类 | `intent`、`confidence` |
| 03 `semantic_retriever` | 租户授权检索范围 | 获取发布指标、字段、术语和版本 | `semantic_candidates`，无权限不得召回 |
| 04 `ambiguity_resolver` | 候选指标/时间/单位 | 识别歧义，必要时 `interrupt()` | `clarification` 或确认对象 |
| 05 `plan_builder` | 已确认语义和问题 | 生成受约束 QueryPlan；不输出执行授权 | `candidate_plan` |
| 06 `plan_validator` | 候选计划、权限快照 | 类型/范围校验；调用 Java 预检 | `validated_plan` 或明确拒绝 |
| 07 `query_tool` | 已验证计划与短时上下文 | 调 Java 受控 QueryService，重试幂等 | `query_id`、受限结果引用 |
| 08 `result_verifier` | 查询结果、质量、版本 | 检查非空、区间覆盖、金额和数据质量 | `facts` 或 `cannot_answer` |
| 09 `chart_planner` | 可用字段、事实数据 | 输出 JSON Schema 合规 chart_spec | 合法图表或无图表 |
| 10 `answer_writer` | 事实、证据、假设、限制 | 写解释与建议，区分事实/推测 | 结构化 answer |
| 11 `audit_usage` | 任务状态、模型调用统计 | 上报实际 Token 和工作流证据 | 幂等结算和最终状态 |

`ambiguity_resolver` 和需要人工确认的节点使用 LangGraph 中断机制；同一 `thread_id` 恢复前 Java 重新检查身份、租户、权限版本、资产版本和配额。节点恢复时会重新执行中断节点的前置部分，因此副作用必须放在批准之后，并且所有外部工具具有确定的幂等键。

### 12.3 受控 GraphState 示例

```python
from typing import TypedDict, NotRequired

class AiBiState(TypedDict):
    request_id: str
    tenant_id: str              # 仅来自服务端已验签上下文
    member_id: str
    workspace_id: str
    permission_version: int
    conversation_id: str
    thread_id: str
    question: str
    semantic_version: NotRequired[str]
    candidate_plan: NotRequired[dict]
    query_id: NotRequired[str]
    result_ref: NotRequired[str]
    facts: NotRequired[list[dict]]
    chart_spec: NotRequired[dict]
    answer: NotRequired[dict]
    error_code: NotRequired[str]
    tool_call_count: NotRequired[int]
```

**状态安全：** 模型不能自由写入 `tenant_id`、`member_id`、`permission_version`。框架状态只是执行数据载体，所有节点调用 Java 工具时仍使用只读的服务端认证上下文。状态中优先存结果引用和计划摘要，不长期写入大量企业明细或模型私有推理链。

### 12.4 Checkpoint 与恢复

生产环境推荐独立 Redis 持久化实例、支持官方 Python `AsyncRedisSaver` 的插件版本、开启 AOF/副本及备份。Cache Redis 使用可淘汰策略；Checkpoint Redis 不允许 `allkeys-*` 淘汰。`thread_id` 为服务端生成的 tenant 绑定不可预测标识，与 `ai_conversation` 记录相映射，恢复时检查 `(tenant_id, member_id, conversation_id)` 归属。数据库中的 checkpoint 数据敏感字段加密/脱敏，设置保留期及租户注销清理。模型不可用时 Graph 返回可解释错误，传统 BI 仍能运行。

### 12.5 工具清单与最小权限

| Tool | 功能 | 限制 |
|---|---|---|
| `catalog_search` | 搜索已发布语义资产 | 内部接口经 tenant/workspace/permission 过滤 |
| `metric_get` | 获取指标定义、公式与口径 | 仅返回当前用户可见资产及版本 |
| `query_preview` | 检查计划有效性及资源成本 | 不返回真实越权数据 |
| `execute_query` | 调用 Java QueryService | 只接受 QueryPlan，不能传 SQL 管理员凭据 |
| `chart_spec_build` | 生成前端图表规范 | 字段必须来自实际查询结果 |
| `report_draft` | 生成报告文字草稿 | P1 导出另走 Java 审批与任务 |

工具名称、参数 schema、最大次数由服务端固定。默认每个请求最多 8 次工具调用、最多 2 次同权限范围的查询修正、60 秒总时限；每个节点和外部模型独立设置 timeout。任何修复都不能自行放松 tenant、RLS、列权限、数据版本或资源限额。

### 12.6 LLM 提示词与数据外发治理

提示词分 system（固定平台规则）、developer（工作流约束）、trusted tool（服务端签名元数据）、untrusted context（源端注释/业务文本/用户输入），禁止不可信文档修改角色或增加工具。数据发送前经过 `ModelDataPolicy` 决策：`DISABLED`、`AGGREGATE_ONLY`、`REDACTED`、`APPROVED_DETAIL`；默认外部模型关闭，租户管理员完成供应商、地区和数据外发策略确认后才可启用。隐藏列不得参与提示词、语义召回或者工具返回。

### 12.7 正确率评估

至少 200 条标注标准问题，覆盖总量、分组、排名、趋势、同环比、退款、时区、零分母、歧义、缺失、权限、Prompt Injection 和跨租户。判定结果是否正确必须通过真实查询及独立业务基准，不能使用模型自身打分作为唯一依据。报告分列 `execution_success_rate`、`answer_correctness`、`clarification_rate`、`refusal_rate`、`unauthorized_leak_count`；目标受支持场景正确率 ≥95%，越权泄漏 0。

## 13. AI API、流式协议与任务幂等

### 13.1 公网到内部的调用链

对外仅 Java 暴露 `/api/v1/ai/*`；Java 通过内网 mTLS 或服务间身份 + 请求 HMAC/JWS 签名调用 FastAPI `/internal/v1/ai/tasks`。短时令牌包含 `iss`、`aud`、`jti`、`exp`、`tenant_id`、`member_id`、`workspace_id`、`permission_version`、`request_id`、`trace_id` 和可用工具范围。FastAPI 校验签名、目标服务、时间窗和重放，所有工具返回给 Java 前再次授权。

### 13.2 对外接口样例

| Method / URI | 用途 | 典型响应 |
|---|---|---|
| `POST /api/v1/ai/conversations` | 创建会话 | `conversation_id` |
| `POST /api/v1/ai/conversations/{id}/messages` | 创建 AI 请求 | HTTP 202 + `request_id, task_id` |
| `GET /api/v1/ai/tasks/{taskId}/events` | SSE 流式订阅 | `accepted/progress/clarification/result/error/done` |
| `GET /api/v1/ai/tasks/{taskId}` | 断线恢复查询 | 当前状态、最后 sequence |
| `POST /api/v1/ai/tasks/{taskId}/cancel` | 取消执行 | CANCEL_REQUESTED，最终以 worker 确认 |
| `POST /api/v1/ai/tasks/{taskId}/resume` | 回答澄清并恢复 | 新运行 attempt，保持同一个付费任务 |
| `POST /api/v1/queries/execute` | 传统 BI 和 AI 共用查询 | QueryResult + versions |

### 13.3 SSE 事件契约

```json
{
  "event": "result",
  "sequence": 8,
  "request_id": "req_123",
  "task_id": "ait_123",
  "trace_id": "trace_abc",
  "payload": {
    "answer": "9月已支付净销售额为250.00元",
    "facts": [{"metric_version_id": "mv_4", "value": "250.00", "unit": "CNY"}],
    "chart_spec": null,
    "data_freshness": "2026-10-09T09:00:00+08:00",
    "evidence": [{"query_id": "qry_1", "publication_seq": 37}]
  }
}
```

每个事件携带单调递增 `sequence`，Java 对历史事件授权后可按 `Last-Event-ID` 恢复；断连不自动再次扣费或重复调用模型。`result` 事件发送前重新鉴权；交付失败但后端完成不代表应该重复执行计费任务。必要时将流式进度压缩为“节点状态和结果引用”，避免传输大量敏感原始明细。

### 13.4 幂等与结算

请求方通过 `Idempotency-Key` 唯一定义一个 AI 业务任务，绑定 tenant + member + route + payload_hash。一次用户任务只预留一次请求配额；内部节点或模型重试可以累计真实 Token 成本，但不得重复扣除“用户请求次数”。模型供应商成本和平台消费账分开，实际 Token 缺失时标注 `ESTIMATED`。终态结算必须具有唯一 `usage_event_id` 和可重入状态转移。

## 14. 报表、导出、通知与业务告警

### 14.1 导出与报告

报表使用固定指标版本、查询快照、数据质量标识和交付时权限。大文件导出单独 `export_job` 队列，默认上限 100 万行；CSV/XLSX/PDF 均在 Worker 中生成，结果写入 tenant 隔离的对象存储。下载经 Java 网关二次鉴权，短时临时链接默认有效 15 分钟，并支持动态撤销；不能仅凭持有 S3 签名 URL 永久绕过撤销要求。可采用受控下载代理或每次网关鉴权后生成极短有效期对象存储访问授权。

### 14.2 定时订阅

订阅任务保存模板版本、时区、Cron/调度规则、创建者、接收成员及权限策略。执行时检查创建者状态和批次质量；每位接收者在交付时重新校验权限，权限不同则分别执行，默认通知的是需登录的报告链接。任务幂等键建议为 `schedule_id + scheduled_window_start + recipient_id + version`，避免消息重放造成重复发送。

### 14.3 告警引擎

告警规则绑定指标版本、对比期、窗口、阈值、连续次数、抑制时间和通知对象，区分数据未就绪与真正业务异常。状态机：`NORMAL → PENDING → TRIGGERED → ACKNOWLEDGED → RECOVERED`，独立的 `SILENCED` 策略只抑制通知、不清除事件。默认重复告警抑制 30 分钟；恢复事件和告警事件保留完整审计。P1 支持邮件/企业 IM 可插拔通知实现。

## 15. SaaS 套餐、配额与支付

### 15.1 套餐模型

套餐定义 `plan_version`，包含 `seat_limit`、`datasource_limit`、`workspace_limit`、`storage_bytes_limit`、`sync_frequency_limit`、`interactive_query_concurrency`、`ai_request_limit`、`ai_token_limit`、`export_rows_limit` 和留存期。价格不写死在后端，按价格版本、币种、税费策略、有效期管理。支持试用、标准、专业和企业套餐，但实际售价、支付渠道和税务规则需商业方确认。

### 15.2 强一致配额控制

配额以 MySQL 为权威账本，先 `reserve`（原子比较更新）→ `commit`（实际用量）/ `release`（未使用部分）；在并发场景下使用唯一执行 ID 防止重复扣费，Redis 仅作为流量前置保护和用量展示缓存。席位在邀请被接受时原子占用；订阅到期只改变可用操作与宽限状态，不删除历史数据。

### 15.3 订单与支付回调

`CREATED → PENDING_PAYMENT → PAID / FAILED / CANCELLED`；退款独立生成记账事件。支付回调必须验签、验金额币种和订单归属，重复事件以 `provider_event_id` 去重；不得以用户前端跳转认定付款成功。升级默认支付成功后即时生效，降级下周期生效，超出新套餐的存储和席位需提供整改说明，不自动删数据。

### 15.4 租户退出与数据保留

所有者重新认证后发起退出任务，先导出可授权资产、工作空间、语义定义、审计和事实数据，然后按配置窗口清理 MySQL、ClickHouse、对象文件、索引、缓存、LangGraph checkpoint。建议撤回窗口 7 天、在线清理 30 天、备份自然淘汰最长 90 天（以合同为准）。恢复系统时重放删除标记，不能将已注销租户“复活”。

## 16. 安全架构、审计与威胁防护

### 16.1 威胁模型

| 攻击/故障 | 防护措施 | 强制验收 |
|---|---|---|
| 越权改 tenant_id/猜测资源 ID | 服务端成员映射 + 资产归属 + 强制 tenant 谓词 | 跨租户泄漏 0 |
| 利用 AI 生成恶意 SQL | QueryPlan DSL、AST 白名单、只读 DB 凭据 | DDL/DML/系统表/外部函数拒绝 |
| Prompt Injection | 不可信内容隔离，服务端固定工具白名单 | 文档不得修改工具或权限 |
| 连接器 SSRF | 目标网络白名单、DNS/rebinding 和重定向校验 | 云元数据和保留内网地址拒绝 |
| 文件上传恶意负载 | 格式与魔数、宏执行禁用、AV 扫描、内容隔离 | 非法文件无法入库发布 |
| 下载 URL 权限过期 | 网关每次鉴权、撤销/短时授权 | 撤销后旧链接拒绝 |
| 配额并发穿透 | MySQL 原子预留、唯一流水、幂等 | 配额不为负、不重复扣款 |
| 重复写入和批次不完整 | generation+publish_seq+业务键逻辑去重 | 重试后金额、行数不重复 |
| 敏感日志/Checkpoint 泄漏 | 脱敏、密钥引用、最小保留和访问隔离 | 默认无敏感完整明细 |

### 16.2 服务间信任

公网统一 TLS，内部建议 mTLS 或工作负载身份，敏感内部请求另带短时签名任务上下文。KMS 管理数据库凭据加密密钥；每个服务使用专属账户，不共享 root。生产/测试物理或网络隔离，测试仅用脱敏数据。网络策略默认拒绝，再批准 Java↔AI、Worker↔源端、Java↔数据仓库等必要链路。

### 16.3 审计字段与不可篡改策略

`event_id`、`tenant_id`、`actor_member_id`、`actor_type`、`action`、`resource_type/id`、`decision_id`、`permission_version`、`trace_id`、`source_ip`、`result`、`event_time`、`summary_hash`。审计服务追加写入，业务成员无法更新或删除；P1 实施存储对象锁、归档哈希链/批次签名等完整性增强。在线保留 180 天、归档 365 天为建议默认值，并按合同调整。操作日志、费用流水与企业数据结果分开存储。

## 17. API 标准、错误与事件协议

### 17.1 REST 契约

统一前缀 `/api/v1`，使用 OpenAPI 3.x 管理请求/响应 schema；列表支持限制最大 page size、稳定排序和游标或分页。创建异步任务返回 `202 Accepted` 与 `task_id`；创建订单、触发同步、AI、导出要求 `Idempotency-Key`。更新已发布或乐观锁资源时必须携带版本号，不允许静默覆盖。

### 17.2 通用响应

```json
{
  "code": "OK",
  "message": "成功",
  "data": {"id": "dsv_101", "version": 5},
  "trace_id": "01JABCDE...",
  "timestamp": "2026-10-09T16:00:00+08:00"
}
```

HTTP 语义建议：400 参数错误、401 未登录、403 无权限、404 隐藏资源（对枚举攻击采用统一响应）、409 版本/幂等冲突、422 无法编译或业务校验失败、429 超配额/限流、503 外部依赖暂不可用。错误码采用稳定枚举 `TENANT_NOT_ACTIVE`、`POLICY_DENIED`、`SEMANTIC_AMBIGUOUS`、`DATA_NOT_PUBLISHED`、`QUERY_QUOTA_EXCEEDED`、`AI_PROVIDER_UNAVAILABLE`；返回可操作的修复建议和 trace_id，不含堆栈、连接串及 SQL 原始敏感参数。

### 17.3 领域事件

| event_type | 生产者 | 消费者 | 幂等键 |
|---|---|---|---|
| `TenantMemberChanged` | iam | cache/worker/audit | `event_id` |
| `DatasetPublished` | semantic | query/cache/ai-index | `event_id` |
| `BatchPublicationCompleted` | ingestion | cache/alerts/dashboard | `batch_id + publish_seq` |
| `AiTaskCompleted` | ai-bridge | usage/report/audit | `request_id + terminal_version` |
| `SubscriptionChanged` | saas | quota/tenant/audit | `order_event_id` |

Outbox 表具有 `event_id`、`aggregate_id`、`tenant_id`、`schema_version`、`created_at`、`published_at` 和重试次数；消费者用 Inbox/去重记录处理至少一次事件。Schema 变更遵循向后兼容和逐步迁移，不能在消费服务全部升级前删除必需字段。

## 18. 部署拓扑、基础资源和发布配置

### 18.1 环境隔离

- **local：** Docker Compose 启动 MySQL、ClickHouse、RabbitMQ、Redis Cache、Redis Checkpoint、MinIO、Java 与 Python；模拟租户隔离测试。
- **test/staging：** 使用接近生产的 K8s 网络策略、对象存储、密钥管理和真实故障注入；敏感数据必须脱敏。
- **production：** 至少 2 个 Java API、2 个 FastAPI，Worker 按同步/导出/交互分池；MySQL 高可用、ClickHouse 可恢复副本部署、RabbitMQ quorum queues、Redis 持久化和备份、S3 多副本存储。

### 18.2 参考容量与配置

按需求基线给出的示例负载：100 租户、1000 用户、100 并发活跃用户、事实表 1000 万行、日增 100 万行，初始容量建议 Java 2×(4C/8GB)、Python 2×(4C/8GB)、MySQL 8C/16GB、ClickHouse 16C/64GB，另配独立 Worker 与对象存储。此配置仅为压测起点，不等于生产规模保证。实际要根据连接器抽取模式、AI 模型供应商、热数据窗口、压缩比、并发查询复杂度核定。

### 18.3 Kubernetes 工作负载建议

| 工作负载 | 类型 | 关键约束 |
|---|---|---|
| frontend | Deployment/CDN | 静态发布，缓存策略，回滚版本 |
| java-api | Deployment + HPA | ≥2 副本，readiness/liveness，PodDisruptionBudget |
| ai-api | Deployment + HPA | ≥2 副本；独立模型并发、外发控制 |
| sync-worker | Deployment/队列伸缩 | 限制连接源端、任务续租和终止处理 |
| export-worker | Deployment/队列伸缩 | 隔离 CPU/内存/磁盘，避免影响交互 |
| mysql/clickhouse/redis/rabbitmq | 托管服务或 HA Stateful workloads | 独立持久卷、备份、恢复、故障域 |
| monitoring | OTel Collector + Metrics/Logs/Traces | 可观测性，不默认采集敏感明细 |

**部署要求：** 关键配置通过 Secret/KMS 引用注入，不写在镜像与 Git；按环境控制允许的模型数据处理区域；Ingress 只暴露公开服务，内部 Python/DB 不提供互联网入口；API、Worker 的 Pod 使用只读根文件系统和最小权限 ServiceAccount（可用时）。

### 18.4 性能降级路线

容量告警时依次降低非关键后台同步优先级、限制报表导出、限制高成本 AI 任务、延长非关键看板刷新周期；保留有效的基础看板和查询。AI 供应商熔断不得拖垮 Java API；单租户超载只限制自己的队列和预算，不应将其他租户请求全部压制。

## 19. 高可用、容灾、备份与一致性恢复

### 19.1 可靠性目标

商业版设计目标为月可用性 99.9%；建议 MySQL RPO ≤15 分钟、RTO ≤4 小时；分析数据已发布批次 RPO ≤1 小时、RTO ≤8 小时。全部指标为需求给出的设计目标，只有通过真实压测、备份核验与恢复演练，并写入合同/运行手册后才可以宣称 SLA。

### 19.2 分层备份

- **MySQL：** 全量备份 + binlog 增量 + 每日可恢复性核验；对租户成员、授权、发布指针和计费流水优先保障恢复一致性。
- **ClickHouse：** 分区/数据库快照 + 增量（按选定产品能力）+ 表 schema + 数据版本清单；不得假设源系统随时可以全量重放。
- **对象存储：** 源文件、质量报告、导出和审计归档按生命周期与版本策略备份，租户退出清理与恢复后删除标记一致。
- **RabbitMQ/任务：** 任务记录以 MySQL 为权威，消息可重发；消费者幂等，死信可审计补偿。
- **Redis Checkpoint：** AOF、备份、恢复测试和独立容量告警，恢复后 Java 检查 AI 任务授权与幂等状态。
- **KMS/Secrets：** 备份密钥引用和恢复流程；不把客户密钥明文打包在备份文件里。

### 19.3 恢复编排

恢复顺序：基础身份与 KMS → MySQL 权威表、租户退出标记、订阅状态 → ClickHouse 与发布版本校验 → 队列/Worker 按租约重建待处理任务 → 对象与索引 → 缓存无效化 → AI checkpoint 引用与权限重新校验。重要规则是不能在 CH 数据未达到 MySQL 指针可见版本时对外宣称批次成功；可暂时回退到上一已验证发布版本，或返回 `DATA_NOT_READY`。

### 19.4 故障注入矩阵

| 故障点 | 预期行为 | 验证证据 |
|---|---|---|
| CH 写入完成、MySQL 发布前宕机 | 旧数据仍可读，同批重试不重算金额 | 批次指针和聚合对账 |
| MySQL 指针推进后 CH 副本落后 | 查询阻断新版本/切换健康副本，不返回残缺结果 | 读侧健康、日志和错误码 |
| Worker 领取任务后失联 | 租约到期重新领取，fencing 防双发布 | 两个 worker 并发测试 |
| AI Graph 暂停后权限撤销 | 恢复/交付失败；不发送历史明细 | 授权版本、SSE 验证 |
| 模型服务断开 | AI 错误清楚，普通看板保持可用 | 熔断与业务监测 |
| 单租户高成本查询洪峰 | 限制其查询预算，其他租户仍可用 | 多租户混合压测 |
| Redis 缓存清空 | 允许受控降级回源，不能提升权限 | 权限回源与延迟统计 |
| 全站恢复后已注销租户 | 删除标记阻断复活并触发二次清理 | 退出恢复用例 |

每季度至少做一次实际恢复演练，形成操作记录、耗时、数据完整性、租户权限验证和复盘改进项。

## 20. 可观测性、运维运行手册与成本治理

### 20.1 指标体系

采用 OpenTelemetry 统一 trace/span，Prometheus 收集系统指标，Grafana 观测，日志平台统一检索，所有组件贯穿 `trace_id`、`tenant_id_hash`、`service`、`request_id`、`task_id`。监控指标默认不直接使用 member_id / conversation_id 作为 label，避免高基数与隐私暴露。

| 层级 | 关键监控 | 推荐告警 |
|---|---|---|
| API | 请求量、P50/P95/P99、错误率、线程/连接池 | API P95 超标、5xx 激增 |
| 查询 | 扫描行数、耗时、并发、终止数、CH CPU/内存/parts | 查询池耗尽、单租户异常 |
| 同步 | lag、水位、失败批次、质量失败、发布耗时 | 数据过期、连续失败 |
| AI | 结果正确率抽样、超时、模型失败率、tool 次数、Token | 成本异常、供应商熔断 |
| SaaS | 配额预留、结算失败、支付回调重复、账单差异 | 负余额风险、未结算积压 |
| 安全 | 403/429、权限撤销、密钥读取、SSRF 拦截 | 越权模式与特权异常 |

### 20.2 Runbook 和应急响应

每类 P1/P0 运行告警必须绑定负责人、影响范围、预警阈值、回滚开关、取证位置和恢复步骤。示例：`SYNC_LAG_HIGH` → 判断连接源是否受限 → 检查最后发布版本和水位 → 检查死信 → 保持旧批次并标记新鲜度 → 按 `batch_id` 安全重试 → 对账通过再发布；禁止人为跳过质量检查强行设置成功状态。

### 20.3 模型成本治理

按 `tenant + model + user task + provider billing record` 统计 Token 和实际/估算费用，分别记录 prompt、completion、工具调用及异常重试。对长历史做检索摘要、限制检索条数和返回数据规模；只在授权范围内使用缓存或安全聚合结果。模型不可用或租户禁止外发时，系统必须提供手动指标查询与传统看板作为替代路径。

## 21. 测试策略、性能基准与上线门槛

### 21.1 测试分层

- **单元测试：** IAM 策略表达式、指标公式、时间规则、Query AST、配额 CAS、Graph 节点行为。
- **契约测试：** OpenAPI、内部签名请求、SSE 事件次序、Outbox 事件版本与类型。
- **集成测试：** Testcontainers/独立测试环境连接 MySQL、ClickHouse、Redis、RabbitMQ 和对象存储。
- **端到端：** 从租户开通、导入/同步、指标发布、看板、AI、导出到停用/退出的闭环。
- **安全测试：** 跨租户、并发权限撤销、提示词注入、连接器 SSRF、任意 SQL、文件/对象 URL 和敏感日志。
- **故障与容量测试：** 数据写入/发布中断、消息重复、ClickHouse 读侧滞后、模型供应商错误、100 并发混合业务。

### 21.2 可执行验收矩阵

| Case ID | 场景 | 判定标准 |
|---|---|---|
| TEN01 | 同一账号加入甲乙两个租户，猜测 ID、切换、查看缓存/AI/下载 | 零跨租户明细泄漏，历史不可见 |
| IAM01 | 角色更改、行级授权、列禁用、异步任务撤销 | 不依赖短 TTL；新请求拒绝，已排队任务重鉴权 |
| ING01 | 写 CH 后发布前崩溃、重复消息、迟到更新、删除 | 无漏数；可见金额不重算；质量状态正确 |
| SET01 | 重复维度业务键、一对多误配 | 阻止错误发布或按批准规则准确去重 |
| MET01 | 支付 100 元 + 支付 200 退款 50 + 取消 300 | 净销售额 250 元；时间规则正确 |
| QRY01 | SQL 注入、恶意子查询、系统表、越权列、取消 | 拒绝执行；真正停止数据库查询 |
| VIS01 | 同一看板不同成员不同权限，单图失败 | 数据各自正确、页面不白屏 |
| AI01 | 200 题标注集、澄清、无数据、越权和 Prompt Injection | 支持场景正确率 ≥95%，安全泄漏 0 |
| REP01 | 不同接收者收到定时报告；权限撤销 | 无权用户无法收到/下载原明细 |
| BIL01 | 100 并发额度预留、回调重放 | 配额不负数；不重复延长订阅 |
| OPS01 | 恢复演练、模型降级、单租户压测 | 恢复到目标状态且无跨租户影响 |

### 21.3 性能设计目标（需实际测量）

| 场景 | P95 设计目标 | 测试前提 |
|---|---|---|
| 普通元数据 API（服务端） | ≤500ms | 不含未明确界定的客户端 WAN |
| 聚合/缓存图表查询 | ≤3s | 指定扫描规模、缓存比例和并发 |
| 常用看板首屏可操作 | ≤5s | 指定浏览器和网络，非所有图表全量渲染 |
| 已支持 AI 问题 | ≤20s | 明确模型供应商、问题集和并发，不含澄清等待 |
| AI 单请求硬时限 | 60s | 达到时执行真正取消与可解释错误 |
| 普通交互查询时限 | 30s | 包含队列时间策略在测试契约中说明 |

混合负载须同时包含同步、查询、AI、导出和权限变更；分别报告 P50/P95/P99、吞吐、错误率、资源用量、质量正确率与隔离测试结果。不得以降低授权检查、结果完整性或跳过 batch 校验换取性能数据。

### 21.4 发布门禁

P0 封闭试用前：所有 P0 FR 关联接口/页面/实体/测试；核心安全、查询、同步回放和金额计算用例 100%通过；阻断级缺陷零遗留，完成基本备份恢复演练。P1 付费版：加上 SSO/MFA、计费回调、报告订阅、告警、成本、支持授权及完整运维演练；发布前评估法律、隐私、客户数据处理地区、模型合同等业务政策。

## 22. CI/CD、工程治理与代码交付

### 22.1 质量流水线

提交 → 格式化/类型检查 → 密钥扫描/SBOM/依赖风险扫描 → Java/Python/前端单测 → OpenAPI/Event Schema 契约测试 → 测试环境容器集成测试 → 跨租户与数据一致性回归 → 镜像签名与构建物归档 → 灰度发布 → 业务回归与监控验证 → 正式放量。

数据库迁移采用向后兼容的 Expand → Migrate → Contract 三阶段；对新增列和旧消费者提供兼容期。服务镜像使用不可变 tag，按版本关联 DDL、语义模型及 Graph 配置；灰度发生异常时先回滚应用，不能盲目回滚可能破坏业务的已生效 Schema 或指标版本。

### 22.2 测试数据与种子数据

初始化 `tenant_A`、`tenant_B` 两个公司，包含同名部门、相同订单业务 ID、跨部门成员与显式拒绝策略。固定销售样例 A=100 元、B=200 元退款 50 元、C=300 元取消；指标值 250 元。数据库迁移生成器、测试 fixtures 和性能样例使用独立测试租户，禁止用生产明细填充测试环境。

### 22.3 评审与变更

关键 ADR 需产品、Java、数据工程、安全、AI、测试共同评审。对 Query DSL、RLS、ClickHouse 版本发布、LangGraph checkpoint 与计费事件变更应具备专项 RFC、向后兼容策略、回归测试和失败回滚方案。所有接口和模型字段标注 owner、隐私等级、生命周期和需求编号。

## 23. 开发实施顺序与里程碑

### 23.1 P0 分阶段交付（建议，不构成交付工期承诺）

| 阶段 | 工作包 | 完成标志与依赖 |
|---|---|---|
| M0 基础工程 | 仓库、环境、CI、配置、日志、ORM 迁移、OpenAPI | 本地一键运行、基础链路可追踪 |
| M1 SaaS 身份底座 | 租户、成员、工作空间、会话、RBAC/ABAC、审计 | TEN01/IAM01 通过后继续 |
| M2 数据接入 | MySQL/PostgreSQL 只读连接、CSV/XLSX、安全校验、元数据 | 已有可重复运行的原始抽取 |
| M3 数据可靠性 | Worker、任务、质量、全量/增量发布、对账、故障恢复 | ING01 通过并固化发布协议 |
| M4 可信分析 | 数据集、关联、指标版本、Query DSL/Compiler、CH 执行 | SET/MET/QRY 测试全部通过 |
| M5 BI 体验 | 图表、看板、权限查看、交互和失败隔离 | 完整销售数据看板从零可交付 |
| M6 AI BI | FastAPI、LangGraph、检索、澄清、受控工具、事实溯源 | 200 题评测与提示词安全测试通过 |
| M7 试点验收 | 混合压测、恢复演练、运维、客户实施模板 | 形成可回归的封闭试用版本 |

### 23.2 P1 商业化增强

订阅购买、支付和配额原账本；MFA/OIDC；报告导出与定时发送；告警与通知；模型供应商网关和成本管理；平台运营控制台；客户支持临时授权；商业级多环境部署、备份、SLA 监控、审计保护与租户退出。P2 再实施预测、统计异常和增强检索，不提前耦合核心权限与版本发布链路。

### 23.3 团队分工建议

前端负责组件规范、权限展示、图表交互及端到端体验；Java 负责 API、租户、权限、查询编译、资产和计费；Python 负责 Graph、Prompt/Tool、安全测试和 AI 标注评测；数据工程负责 connector、ClickHouse 数据模型、增量和质量；测试负责独立口径基准、故障注入和隔离压测；运维负责 CI/CD、密钥、监控和恢复。跨模块涉及协议需通过版本化 `contracts/` 共同评审。

## 24. 待确认事项、风险与技术设计冻结条件

### 24.1 必须业务签署的事项

首批销售与零售口径（销售额/退款/时区/汇率）；模型服务区域、外发策略和供应商；客户数据库接入网络方式；套餐价格、税务、支付与退款流程；高级版物理隔离选项；日志/文件/会话保留政策；RPO/RTO 与正式容量承诺。未确认时遵循需求默认配置，不得推断客户已接受。

### 24.2 需要原型验证的技术问题

| 风险编号 | 验证主题 | 最小验收实验 |
|---|---|---|
| R01 | ClickHouse 大表按业务键+版本查询成本 | 1000 万事实/并发 100，含增量修订与多租户 RLS，测 P95/正确性 |
| R02 | 全量切换/增量可见性 | 写入成功但 MySQL 未发布、CH 副本滞后、跨月更新三类故障 |
| R03 | Redis LangGraph checkpoint 兼容性与备份 | Python 重启、Redis 故障恢复、中断复用、权限撤销 |
| R04 | Java Query AST 安全覆盖 | 子查询、Join、禁列过滤、非法函数、特殊字符 fuzz 测试 |
| R05 | 受控 Connector 网络边界 | DNS rebinding、重定向、内网网段及云元数据防护 |
| R06 | 混合业务限额与公平性 | 20 租户并发查询+同步+导出+AI，测旁路影响 |
| R07 | AI 标准问答正确率 | 200 条独立标注问题、失败分类与同样例确定性回归 |
| R08 | 审计与计费幂等 | 同时请求、重放 webhook、超时重试、账本校验 |

### 24.3 本文档冻结门槛

开发启动前至少冻结 P0 API 版本、用户角色策略、核心 MySQL 表与 CH 事实模型、发布协议、查询 DSL/允许 SQL 子集、LangGraph 节点契约与 checkpoint 方案、网络与密钥策略。P1 能力允许在模块接口保持稳定的前提下迭代。实际组件版本须在 `architecture-lock.md` 中列出发行版、兼容矩阵、镜像摘要和已验证安全配置。

## 附录 A. 需求追踪交付清单

| 交付物 | 文件或目录 | 主要负责人 |
|---|---|---|
| 技术架构设计/ADR | `docs/architecture/*` | 架构负责人 |
| MySQL 完整 DDL、ER 与索引 | `infrastructure/migrations/mysql/*` | Java/DBA |
| ClickHouse DDL、视图与正确性 SQL | `infrastructure/migrations/clickhouse/*` | 数据工程 |
| QueryPlan JSON Schema/AST 与安全基准 | `contracts/query-plan.schema.json` | Java/安全 |
| Java REST OpenAPI | `contracts/openapi.yaml` | Java/前端 |
| Python 内部 API 与 Tool schemas | `ai-service/app/schemas/*` | Python/Java |
| 流式协议与事件 Schema | `contracts/events/*` | Java/Python |
| 图表 JSON Schema | `contracts/chart-spec.schema.json` | 前端/Java |
| CI、Helm、资源配额与 NetworkPolicy | `infrastructure/*` | 运维 |
| 租户隔离、对账、AI 标注与恢复测试 | `tests/*` | 测试/安全 |
| 故障应对、容灾与退出 runbook | `docs/runbooks/*` | 运维/实施 |

## 附录 B. 资料依据和实现核验来源

1. 用户提供：《商业化 AI BI 智能数据分析平台——完整系统需求规格说明书 V1.0》，2026-10-09。本文档中的 FR/NFR、P0/P1/P2、数字性能目标和业务约束以上游为准。
2. LangChain 官方：LangGraph Memory 与持久化 checkpointer，https://docs.langchain.com/oss/python/langgraph/add-memory （包含 Redis、PostgreSQL 等生产持久化方案）。
3. LangChain 官方：LangGraph Persistence，https://docs.langchain.com/oss/python/langgraph/persistence （线程、checkpoint 与恢复）。
4. ClickHouse 官方：Deduplication strategies，https://clickhouse.com/docs/concepts/features/operations/insert/deduplication （区分后台数据合并与查询正确性）。
5. ClickHouse 技术说明：When to use OPTIMIZE TABLE ... FINAL，https://clickhouse.com/resources/engineering/clickhouse-optimize-table-final （更新型表在查询端显式去重的边界）。
6. Spring Security 官方：OAuth2 Resource Server Multi-tenancy，https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html （认证服务器多租户与 claims 解析；本系统业务租户授权仍由自有 PolicyDecisionService 负责）。

---

**技术设计结论：** 先建立“租户身份及权限 → 稳定数据接入与可见批次 → 可版本化语义层 → 受控只读查询”四个可信底座，再在其上增加 Vue 看板与 LangGraph AI。AI 只能调用受限的已授权查询服务，不参与修改企业业务事务，也不能替代正式的数据校验、验收与审计。本文为可进入详细数据库设计、API 契约设计及工程初始化的评审稿，未宣称系统已经完成实施或通过压测。
