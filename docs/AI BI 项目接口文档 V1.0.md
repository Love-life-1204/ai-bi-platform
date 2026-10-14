# 商业化 AI BI 智能数据分析平台｜项目接口文档 V1.1

**文档编号：** AI-BI-API-001  **日期：** 2026-10-10  **状态：** 路由规范调整版（非生产 API 声明）  **接口格式：** OpenAPI 3.1.1

> 编制依据：《完整系统需求规格说明书 V1.0》《详细技术架构设计 V1.0》《完整数据库设计 V1.0》《前后端项目初始化及基础代码 V1.0》《AI 智能分析引擎设计 V1.0》《系统高并发与稳定性开发规范 V1.0》。本文档保留已有接口兼容性信息，新增接口均为设计待实现。

## 1. 目标、范围和实现现状

本接口文档面向 Vue3 前端、Spring Boot 业务后端、FastAPI/LangGraph AI 服务、只读分析引擎与第三方集成。共编制 **149 个对外 API 操作**，分布于 **19 个模块**；另有 **5 个内部服务接口**。其中 **6 个接口在旧基础工程中有对应业务能力，但旧实现实际使用 `/api/v1`，需要统一路由及字段转换**，其余为目标契约，均未在本次任务中部署验证。

**范围标识：** P0 为封闭试用必备；P1 为商业试运营增强。性能、并发与恢复指标沿用需求基线，但需要真实部署验证。禁止对外宣称全部接口已实现。

## 2. 系统边界、URL 与版本治理

浏览器仅调用 Spring Boot 公共网关 `/api`（无版本号）；不能直连 FastAPI、ClickHouse 或数据源。Java 调 FastAPI `/internal/v1/ai/...`，Python 调 Java `/internal/v1/queries/...`。内部服务独立网络、独立凭证和访问控制；AI 只有候选查询计划权，Java 拥有最终查询授权权。

版本规则：**所有对外 REST 接口路径固定以 `/api` 开始，不在 URL 公开 `v1/v2`。** 以 OpenAPI 的 `info.version` 按 SemVer 管理接口契约（本版 `1.1.0`），非破坏性演进保持原 URL；不兼容变更在开发评审后选择独立兼容路由、临时双路由或 `Accept` 媒体类型协商，并提供迁移期限及弃用告警。内部服务路径继续使用 `/internal/v1`，与公共路由策略独立。仅升级 OpenAPI 文档版本并不会自动实现运行时兼容。

### 2.1 请求统一约定

| 项目 | 统一要求 |
|---|---|
| HTTPS | 公网强制 TLS，禁止 URL 查询参数传递访问令牌 |
| 身份 | `Authorization: Bearer <access_token>`；JWT 由 Java 校验签名、时效和撤销版本 |
| 租户 | 当前租户由服务端确认的会话确定；客户端传入 tenant_id 不可作为授权依据 |
| 链路 | 请求允许 `X-Request-ID`，响应返回 `trace_id`；不可信 trace 不等于身份 |
| 金额 | 金额/高精度数值采用字符串，服务端使用 Decimal/BigDecimal |
| 时间 | ISO 8601 带时区；业务时间按租户时区，左闭右开 |
| 分页 | `page>=1`, `1<=page_size<=100`；稳定排序；列表含 page 元数据 |
| 幂等 | 订单、AI 提问、导入、同步、导出等必须传 `Idempotency-Key` |
| 更新 | `PATCH/PUT` 采用 `If-Match` 乐观版本，冲突返回 409/412 |
| 空结果 | 数据为空与权限不足严格区分，禁止泄露未授权对象是否存在 |

### 2.2 成功与错误结构

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "query_id": "qry_100",
    "status": "SUCCEEDED",
    "dataset_version_id": "dsv_12",
    "metric_version_ids": [
      "mv_net_sales_4"
    ],
    "generation_id": "gen_a",
    "publish_seq": "17",
    "columns": [
      {
        "field_id": "f_region",
        "name": "地区",
        "value_type": "string",
        "unit": ""
      },
      {
        "field_id": "mv_net_sales_4",
        "name": "净销售额",
        "value_type": "decimal",
        "unit": "CNY"
      }
    ],
    "rows": [
      {
        "f_region": "华东",
        "mv_net_sales_4": "250.00"
      }
    ],
    "data_freshness": "2026-10-10T09:00:00+08:00",
    "quality": "PASS",
    "truncated": false
  },
  "trace_id": "trace_q",
  "timestamp": "2026-10-10T10:00:00+08:00"
}
```

错误结构为 `code/message/details/trace_id/timestamp`；生产不回传敏感 SQL、源地址、提示词、密码、堆栈或策略明细。对象不可访问时使用统一 404/403 外观，内部审计保留具体判定。

### 2.3 通用 HTTP 状态与稳定错误码

| HTTP | 常见错误码 | 处理要求 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 参数/格式错误，不自动重试 |
| 401 | AUTH_EXPIRED / SESSION_REVOKED | 清会话或刷新一次 |
| 403 | POLICY_DENIED / TENANT_NOT_ACTIVE | 阻止显示结果，不重试 |
| 404 | RESOURCE_NOT_FOUND | 与无权对象采取统一外部响应 |
| 409 | VERSION_CONFLICT / IDEMPOTENCY_CONFLICT | 告知冲突，刷新版本后重试 |
| 412 | PRECONDITION_FAILED | If-Match 不满足 |
| 422 | SEMANTIC_AMBIGUOUS / QUERY_PLAN_INVALID / DATA_NOT_PUBLISHED | 引导澄清或修复指标 |
| 429 | QUERY_QUOTA_EXCEEDED / AI_QUOTA_EXCEEDED | 限流/配额，适用时返回 Retry-After |
| 503 | AI_PROVIDER_UNAVAILABLE / DATA_SOURCE_UNAVAILABLE | 局部降级，不形成无限重试 |
| 504 | QUERY_TIMEOUT / TASK_TIMEOUT | 后端应终止真实执行并释放资源 |

## 3. API 安全策略与多租户约束

**权威租户链：** 身份令牌→当前有效成员→工作空间→对象归属→RBAC/ABAC→行级及列级权限→语义/指标/数据发布版本→配额→执行。分享不增加底层访问权；禁列不得用于筛选/排序/分组/AI 提示词；权限撤销后新请求立即拒绝，执行中的任务在结果交付前复核。

禁止在请求 Schema 中加入 `tenant_id`、`role`、`sql`、`raw_sql` 作为用户声明的执行权限。受控查询使用强类型 `QueryPlan`，所有标识必须解析为当前租户发布资产。数据源凭据只保存 KMS/Vault 引用。

**浏览器流式认证：** 原生 `EventSource` 无法直接设置自定义 Bearer 请求头；前端使用具备 Authorization Header 的 `fetch` 流解析器，或服务端先签发一次性短时流令牌/同站 HttpOnly Cookie（须防 CSRF）。严禁将长期 access token 放进 URL。

## 4. 对外 API 分模块目录

| 领域 | 接口数 | P0/P1 范围 | 典型路径 |
|---|---:|---|---|
| 认证会话 | 7 | P0 | `POST /api/auth/login` |
| 租户组织 | 16 | P0 | `GET /api/tenants` |
| 工作空间 | 5 | P0 | `GET /api/workspaces` |
| 角色授权 | 12 | P0 | `GET /api/roles` |
| 数据源 | 9 | P0 | `GET /api/datasources` |
| 文件与导入 | 6 | P0 | `POST /api/files/uploads` |
| 数据同步 | 9 | P0 | `GET /api/sync-jobs` |
| 数据质量 | 4 | P0 | `GET /api/quality-results` |
| 数据集 | 8 | P0 | `GET /api/datasets` |
| 指标语义 | 10 | P0 | `GET /api/metrics` |
| 受控查询 | 7 | P0 | `POST /api/queries` |
| 图表看板 | 12 | P0 | `GET /api/charts` |
| AI 智能分析 | 13 | P0 | `POST /api/ai/analyze` |
| 报告导出 | 10 | P1 | `GET /api/reports` |
| 告警通知 | 7 | P1 | `GET /api/alert-rules` |
| 商业计费 | 8 | P1 | `GET /api/plans` |
| 平台审计 | 1 | P0 | `GET /api/audit-events` |
| 平台运营 | 3 | P1 | `GET /api/platform/overview` |
| 通用任务 | 2 | P0 | `GET /api/tasks/{taskId}` |

### 4.1—4.N 分模块接口矩阵（“旧演示能力已有”不代表 `/api` 新路径已实施）

### 4.1 认证会话（7项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | 账号密码登录 | PUBLIC | P0 | 已有基础端点 |
| POST | `/api/auth/refresh` | 刷新访问令牌并轮换会话 | PUBLIC | P0 | 待开发 |
| POST | `/api/auth/logout` | 撤销当前会话 | AUTHENTICATED | P0 | 待开发 |
| POST | `/api/auth/password-reset/request` | 申请密码重置 | PUBLIC | P0 | 待开发 |
| POST | `/api/auth/password-reset/confirm` | 确认重置密码 | PUBLIC | P0 | 待开发 |
| POST | `/api/auth/reauth` | 敏感操作重新认证 | AUTHENTICATED | P0 | 待开发 |
| GET | `/api/me` | 读取当前用户租户身份 | AUTHENTICATED | P0 | 已有基础端点 |

### 4.2 租户组织（16项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/tenants` | 列出当前账号已加入租户 | TENANT_READ | P0 | 待开发 |
| POST | `/api/tenants` | 创建企业租户 | TENANT_MANAGE | P0 | 待开发 |
| GET | `/api/tenants/current` | 获取当前租户 | TENANT_READ | P0 | 待开发 |
| PATCH | `/api/tenants/current` | 更新当前租户基础配置 | TENANT_MANAGE | P0 | 待开发 |
| POST | `/api/tenants/switch` | 切换企业并签发新租户会话 | AUTHENTICATED | P0 | 待开发 |
| POST | `/api/tenants/current/freeze` | 冻结当前租户 | TENANT_MANAGE | P0 | 待开发 |
| POST | `/api/tenants/current/exit` | 确认租户退出与数据清理 | TENANT_MANAGE | P0 | 待开发 |
| GET | `/api/members` | 分页查询租户成员 | TENANT_READ | P0 | 待开发 |
| POST | `/api/members/invitations` | 发起成员邀请 | TENANT_MANAGE | P0 | 待开发 |
| POST | `/api/members/invitations/accept` | 接受企业邀请 | TENANT_MANAGE | P0 | 待开发 |
| GET | `/api/members/{memberId}` | 查看成员详情 | TENANT_READ | P0 | 待开发 |
| PATCH | `/api/members/{memberId}` | 变更成员状态及角色 | TENANT_MANAGE | P0 | 待开发 |
| DELETE | `/api/members/{memberId}` | 移除租户成员 | TENANT_MANAGE | P0 | 待开发 |
| GET | `/api/departments` | 获取部门树 | TENANT_READ | P0 | 待开发 |
| POST | `/api/departments` | 创建部门 | TENANT_MANAGE | P0 | 待开发 |
| PATCH | `/api/departments/{departmentId}` | 编辑部门 | TENANT_MANAGE | P0 | 待开发 |

### 4.3 工作空间（5项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/workspaces` | 分页查询工作空间 | WORKSPACE_READ | P0 | 已有基础端点 |
| POST | `/api/workspaces` | 新建工作空间 | WORKSPACE_MANAGE | P0 | 待开发 |
| GET | `/api/workspaces/{workspaceId}` | 查看工作空间 | WORKSPACE_READ | P0 | 待开发 |
| PATCH | `/api/workspaces/{workspaceId}` | 更新工作空间 | WORKSPACE_MANAGE | P0 | 待开发 |
| DELETE | `/api/workspaces/{workspaceId}` | 删除空工作空间 | WORKSPACE_MANAGE | P0 | 待开发 |

### 4.4 角色授权（12项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/roles` | 查询角色列表 | IAM_READ | P0 | 待开发 |
| POST | `/api/roles` | 创建租户自定义角色 | IAM_MANAGE | P0 | 待开发 |
| GET | `/api/roles/{roleId}` | 角色详情 | IAM_READ | P0 | 待开发 |
| PATCH | `/api/roles/{roleId}` | 修改角色 | IAM_MANAGE | P0 | 待开发 |
| DELETE | `/api/roles/{roleId}` | 删除未引用的角色 | IAM_MANAGE | P0 | 待开发 |
| PUT | `/api/roles/{roleId}/permissions` | 替换角色权限集合 | IAM_MANAGE | P0 | 待开发 |
| GET | `/api/permissions` | 读取权限字典 | IAM_READ | P0 | 待开发 |
| GET | `/api/data-policies` | 读取行列级权限策略 | IAM_READ | P0 | 待开发 |
| POST | `/api/data-policies` | 创建数据权限策略 | IAM_MANAGE | P0 | 待开发 |
| PATCH | `/api/data-policies/{policyId}` | 编辑数据权限策略 | IAM_MANAGE | P0 | 待开发 |
| POST | `/api/data-policies/preview` | 预览当前成员有效权限 | IAM_MANAGE | P0 | 待开发 |
| GET | `/api/assets/{assetId}/access` | 查看当前资产有效权限 | IAM_READ | P0 | 待开发 |

### 4.5 数据源（9项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/datasources` | 查询数据源列表 | DATASOURCE_READ | P0 | 待开发 |
| POST | `/api/datasources` | 创建只读数据源配置 | DATASOURCE_MANAGE | P0 | 待开发 |
| GET | `/api/datasources/{datasourceId}` | 数据源详情不含明文密钥 | DATASOURCE_READ | P0 | 待开发 |
| PATCH | `/api/datasources/{datasourceId}` | 更新数据源配置 | DATASOURCE_MANAGE | P0 | 待开发 |
| DELETE | `/api/datasources/{datasourceId}` | 删除无依赖数据源 | DATASOURCE_MANAGE | P0 | 待开发 |
| POST | `/api/datasources/{datasourceId}/test` | 测试连通性与只读权限 | DATASOURCE_MANAGE | P0 | 待开发 |
| POST | `/api/datasources/{datasourceId}/metadata/collect` | 异步采集元数据 | DATASOURCE_MANAGE | P0 | 待开发 |
| GET | `/api/datasources/{datasourceId}/objects` | 查询批准的源对象 | DATASOURCE_READ | P0 | 待开发 |
| GET | `/api/datasources/{datasourceId}/metadata/diff` | 获取结构变化报告 | DATASOURCE_READ | P0 | 待开发 |

### 4.6 文件与导入（6项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| POST | `/api/files/uploads` | 申请安全上传凭证 | IMPORT_MANAGE | P0 | 待开发 |
| POST | `/api/files/uploads/{uploadId}/complete` | 完成上传并执行安全校验 | IMPORT_MANAGE | P0 | 待开发 |
| GET | `/api/imports` | 分页查看导入任务 | IMPORT_READ | P0 | 待开发 |
| POST | `/api/imports` | 创建文件导入任务 | IMPORT_MANAGE | P0 | 待开发 |
| GET | `/api/imports/{importId}` | 获取导入状态和坏行统计 | IMPORT_READ | P0 | 待开发 |
| POST | `/api/imports/{importId}/cancel` | 请求取消导入任务 | IMPORT_MANAGE | P0 | 待开发 |

### 4.7 数据同步（9项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/sync-jobs` | 查询同步任务 | SYNC_READ | P0 | 待开发 |
| POST | `/api/sync-jobs` | 新建全量或增量同步任务 | SYNC_MANAGE | P0 | 待开发 |
| GET | `/api/sync-jobs/{jobId}` | 查询同步配置与水位 | SYNC_READ | P0 | 待开发 |
| PATCH | `/api/sync-jobs/{jobId}` | 修改同步任务 | SYNC_MANAGE | P0 | 待开发 |
| POST | `/api/sync-jobs/{jobId}/run` | 触发同步执行 | SYNC_MANAGE | P0 | 待开发 |
| POST | `/api/sync-jobs/{jobId}/pause` | 暂停同步任务 | SYNC_MANAGE | P0 | 待开发 |
| GET | `/api/sync-runs` | 查看同步运行历史 | SYNC_READ | P0 | 待开发 |
| GET | `/api/sync-runs/{runId}` | 查看批次校验与发布状态 | SYNC_READ | P0 | 待开发 |
| POST | `/api/sync-runs/{runId}/cancel` | 请求取消同步运行 | SYNC_MANAGE | P0 | 待开发 |

### 4.8 数据质量（4项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/quality-results` | 分页查看质量验证结果 | QUALITY_READ | P0 | 待开发 |
| POST | `/api/quality-rules` | 创建质量阻断规则 | QUALITY_MANAGE | P0 | 待开发 |
| GET | `/api/quality-rules` | 查询质量规则 | QUALITY_READ | P0 | 待开发 |
| POST | `/api/quality-runs` | 对指定批次触发质量校验 | QUALITY_MANAGE | P0 | 待开发 |

### 4.9 数据集（8项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/datasets` | 列出有权限的数据集 | DATASET_READ | P0 | 已实现 |
| POST | `/api/datasets` | 创建数据集草稿 | DATASET_MANAGE | P0 | 已实现 |
| GET | `/api/datasets/{datasetId}` | 查询数据集详情 | DATASET_READ | P0 | 已实现 |
| PATCH | `/api/datasets/{datasetId}` | 修改数据集草稿 | DATASET_MANAGE | P0 | 已实现 |
| POST | `/api/datasets/{datasetId}/validate` | 校验字段映射和关联基数 | DATASET_MANAGE | P0 | 已实现 |
| POST | `/api/datasets/{datasetId}/publish` | 发布不可变数据集版本 | DATASET_MANAGE | P0 | 已实现 |
| GET | `/api/datasets/{datasetId}/versions` | 读取历史数据集版本 | DATASET_READ | P0 | 已实现 |
| GET | `/api/datasets/{datasetId}/preview` | 查看受行列权限保护的数据样本 | DATASET_READ | P0 | 已实现 |

### 4.10 指标语义（10项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/metrics` | 查询指标列表 | METRIC_READ | P0 | 已实现 |
| POST | `/api/metrics` | 新建指标草稿 | METRIC_MANAGE | P0 | 已实现 |
| GET | `/api/metrics/{metricId}` | 查询指标和版本 | METRIC_READ | P0 | 已实现 |
| PATCH | `/api/metrics/{metricId}` | 修改指标草稿 | METRIC_MANAGE | P0 | 已实现 |
| POST | `/api/metrics/{metricId}/validate` | 校验公式、时区、汇率和依赖 | METRIC_MANAGE | P0 | 已实现 |
| POST | `/api/metrics/{metricId}/publish` | 发布指标口径版本 | METRIC_MANAGE | P0 | 已实现 |
| GET | `/api/metrics/{metricId}/versions` | 读取指标版本历史 | METRIC_READ | P0 | 已实现 |
| GET | `/api/terms` | 查询业务术语词典 | METRIC_READ | P0 | 已实现 |
| POST | `/api/terms` | 新增业务术语 | METRIC_MANAGE | P0 | 已实现 |
| POST | `/api/terms/{termId}/publish` | 审核发布业务术语 | METRIC_MANAGE | P0 | 已实现 |

### 4.11 受控查询（7项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| POST | `/api/queries` | 提交同步受控查询 | BI_QUERY | P0 | 已有基础端点 |
| POST | `/api/queries/plans/execute` | 执行新版强类型受控查询计划（旧客户端兼容迁移） | BI_QUERY | P0 | 已实现 |
| POST | `/api/queries/async` | 提交长耗时异步查询 | BI_QUERY | P0 | 已实现 |
| POST | `/api/queries/validate` | 校验候选查询计划及资源预算 | BI_QUERY | P0 | 已实现 |
| GET | `/api/queries/{queryId}` | 查看查询状态与追溯信息 | BI_QUERY | P0 | 已实现 |
| GET | `/api/queries/{queryId}/result` | 读取已完成受控查询结果 | BI_QUERY | P0 | 已实现 |
| POST | `/api/queries/{queryId}/cancel` | 终止数据库层实际查询 | BI_QUERY | P0 | 已实现 |

### 4.12 图表看板（12项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/charts` | 查询有权图表 | DASHBOARD_READ | P0 | 已实现 |
| POST | `/api/charts` | 创建图表 | DASHBOARD_MANAGE | P0 | 已实现 |
| GET | `/api/charts/{chartId}` | 读取图表配置 | DASHBOARD_READ | P0 | 已实现 |
| PATCH | `/api/charts/{chartId}` | 更新图表草稿 | DASHBOARD_MANAGE | P0 | 已实现 |
| POST | `/api/charts/{chartId}/validate` | 校验查询计划、字段绑定和安全图表规范 | DASHBOARD_MANAGE | P0 | 已实现 |
| POST | `/api/charts/{chartId}/publish` | 发布图表版本 | DASHBOARD_MANAGE | P0 | 已实现 |
| GET | `/api/charts/{chartId}/versions` | 列出图表历史版本 | DASHBOARD_READ | P0 | 已实现 |
| GET | `/api/dashboards` | 查询有权看板 | DASHBOARD_READ | P0 | 已实现 |
| POST | `/api/dashboards` | 新建看板草稿 | DASHBOARD_MANAGE | P0 | 已实现 |
| GET | `/api/dashboards/{dashboardId}` | 获取当前可见看板 | DASHBOARD_READ | P0 | 已实现 |
| PATCH | `/api/dashboards/{dashboardId}` | 编辑看板草稿与布局 | DASHBOARD_MANAGE | P0 | 已实现 |
| POST | `/api/dashboards/{dashboardId}/publish` | 发布看板不可变版本 | DASHBOARD_MANAGE | P0 | 已实现 |
| GET | `/api/dashboards/{dashboardId}/versions` | 列出有权历史版本 | DASHBOARD_READ | P0 | 已实现 |
| POST | `/api/dashboards/{dashboardId}/query` | 按查看者权限加载图表聚合 | DASHBOARD_READ | P0 | 已实现 |

### 4.13 AI 智能分析（13项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| POST | `/api/ai/analyze` | 兼容旧版同步 AI 分析接口（仅演示） | AI_QUERY | P0 | 已有基础端点 |
| GET | `/api/ai/conversations` | 分页查看授权的会话 | AI_QUERY | P0 | 待开发 |
| POST | `/api/ai/conversations` | 创建工作空间内会话 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/conversations/{conversationId}` | 读取会话与确认上下文 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/conversations/{conversationId}/messages` | 获取授权的历史消息 | AI_QUERY | P0 | 待开发 |
| POST | `/api/ai/conversations/{conversationId}/messages` | 提交提问并生成持久任务 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/tasks/{taskId}` | 读取当前任务状态 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/tasks/{taskId}/events` | 订阅/恢复 SSE 事件 | AI_QUERY | P0 | 待开发 |
| POST | `/api/ai/tasks/{taskId}/resume` | 提交澄清选择后恢复 Graph | AI_QUERY | P0 | 待开发 |
| POST | `/api/ai/tasks/{taskId}/cancel` | 申请中止 AI 任务 | AI_QUERY | P0 | 待开发 |
| POST | `/api/ai/tasks/{taskId}/feedback` | 提交 AI 答案反馈 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/tasks/{taskId}/answer` | 读取校验过的最终答案 | AI_QUERY | P0 | 待开发 |
| GET | `/api/ai/tasks/{taskId}/evidence` | 读取指标版本及查询证据 | AI_QUERY | P0 | 待开发 |

### 4.14 报告导出（10项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/reports` | 分页查询有权报告 | REPORT_READ | P1 | 待开发 |
| POST | `/api/reports` | 创建保存的分析报告 | REPORT_MANAGE | P1 | 待开发 |
| GET | `/api/reports/{reportId}` | 获取授权报告 | REPORT_READ | P1 | 待开发 |
| POST | `/api/exports` | 提交异步报表导出 | REPORT_MANAGE | P1 | 待开发 |
| GET | `/api/exports/{exportId}` | 获取导出任务状态 | REPORT_READ | P1 | 待开发 |
| POST | `/api/exports/{exportId}/cancel` | 申请取消导出任务 | REPORT_MANAGE | P1 | 待开发 |
| GET | `/api/exports/{exportId}/download` | 受控权限下载导出文件 | REPORT_READ | P1 | 待开发 |
| GET | `/api/report-subscriptions` | 获取报告订阅配置 | REPORT_READ | P1 | 待开发 |
| POST | `/api/report-subscriptions` | 创建定时报告订阅 | REPORT_MANAGE | P1 | 待开发 |
| PATCH | `/api/report-subscriptions/{subscriptionId}` | 编辑报告订阅及接收者 | REPORT_MANAGE | P1 | 待开发 |

### 4.15 告警通知（7项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/alert-rules` | 查询业务告警规则 | ALERT_READ | P1 | 待开发 |
| POST | `/api/alert-rules` | 创建业务指标告警 | ALERT_MANAGE | P1 | 待开发 |
| PATCH | `/api/alert-rules/{ruleId}` | 更新告警规则 | ALERT_MANAGE | P1 | 待开发 |
| GET | `/api/alert-events` | 查询告警事件 | ALERT_READ | P1 | 待开发 |
| POST | `/api/alert-events/{eventId}/ack` | 确认告警事件 | ALERT_MANAGE | P1 | 待开发 |
| GET | `/api/notifications` | 读取个人通知 | ALERT_READ | P1 | 待开发 |
| POST | `/api/notifications/{notificationId}/read` | 标记通知已读 | ALERT_MANAGE | P1 | 待开发 |

### 4.16 商业计费（8项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/plans` | 列出可售套餐版本 | BILLING_READ | P1 | 待开发 |
| GET | `/api/subscriptions/current` | 查询当前订阅 | BILLING_READ | P1 | 待开发 |
| POST | `/api/orders` | 创建付费订阅订单 | BILLING_MANAGE | P1 | 待开发 |
| GET | `/api/orders` | 查看租户订单列表 | BILLING_READ | P1 | 待开发 |
| GET | `/api/orders/{orderId}` | 获取订单详情 | BILLING_READ | P1 | 待开发 |
| POST | `/api/subscriptions/current/change-plan` | 申请升级或降级 | BILLING_MANAGE | P1 | 待开发 |
| GET | `/api/usage/quotas` | 读取租户各项配额和用量 | BILLING_READ | P1 | 待开发 |
| GET | `/api/usage/ai` | 读取 AI 请求和 Token 成本统计 | BILLING_READ | P1 | 待开发 |

### 4.17 平台审计（1项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/audit-events` | 按权限分页读取审计事件 | AUDIT_READ | P0 | 待开发 |

### 4.18 平台运营（3项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/platform/overview` | 只读平台运营指标 | PLATFORM_OPS | P1 | 待开发 |
| GET | `/api/platform/tenants` | 平台租户健康元数据清单 | PLATFORM_OPS | P1 | 待开发 |
| POST | `/api/platform/support-grants` | 按工单申请租户授权支持 | PLATFORM_OPS | P1 | 待开发 |

### 4.19 通用任务（2项）

| 方法 | 路径 | 操作说明 | 权限 | 优先级 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/tasks/{taskId}` | 获取后台异步任务状态 | TASK_READ | P0 | 待开发 |
| POST | `/api/tasks/{taskId}/cancel` | 取消授权的后台任务 | TASK_MANAGE | P0 | 待开发 |

## 5. 重点 API 请求、响应与状态约束

### 5.1 登录 · `POST /api/auth/login`

**请求 Schema：** `LoginRequest`；**响应 data：** `AuthTokens`。**业务约束：** 登录请求不返回存在性判断；若启用短时刷新会话，刷新凭证放 Secure+HttpOnly cookie，轮换后旧凭证不可重放。

```json
{
  "account": "demo@example.com",
  "password": "<填写用户密码>",
  "tenant_code": "tenant_a"
}
```

### 5.2 企业切换 · `POST /api/tenants/switch`

**请求 Schema：** `GenericAction`；**响应 data：** `AuthTokens`。**业务约束：** 换租户必须重新签发 tenant/member/permission_version 上下文；清空 Vue Pinia、缓存、SSE 和请求中的旧租户态。

### 5.3 数据源配置 · `POST /api/datasources`

**请求 Schema：** `DatasourceWrite`；**响应 data：** `Datasource`。**业务约束：** 测试连接必须从受控 Worker/网段发起，执行 SSRF / DNS rebinding 防护；源只读。

### 5.4 上传文件 · `POST /api/files/uploads`

**请求 Schema：** `FileUploadRequest`；**响应 data：** `FileUpload`。**业务约束：** 默认 UTF8 CSV / XLSX；单文件上限 100MB、恶意内容校验与对象空间隔离，避免直接接收 Excel 宏执行。

### 5.5 触发同步 · `POST /api/sync-jobs/{jobId}/run`

**请求 Schema：** `无请求正文`；**响应 data：** `TaskAccepted`。**业务约束：** 同目标互斥与租约隔离令牌；写入、质量校验、发布 CAS 成功后才推进水位。

### 5.6 指标发布 · `POST /api/metrics/{metricId}/publish`

**请求 Schema：** `GenericAction`；**响应 data：** `MetricVersion`。**业务约束：** 发布版本不可变，需校验公式/依赖/权限和受影响看板。

### 5.7 受控查询 · `POST /api/queries`

**请求 Schema：** `QueryPlan`；**响应 data：** `QueryResult`。**业务约束：** 固定指标/数据集发布版本，执行列/行策略、查询限额与超时；不能只在 SQL 尾部加 tenant 过滤。

```json
{
  "dataset_version_id": "dsv_12",
  "metric_version_ids": [
    "mv_net_sales_4"
  ],
  "dimension_field_ids": [
    "f_region"
  ],
  "time_range": {
    "field_id": "f_paid_at",
    "start": "2026-09-01T00:00:00+08:00",
    "end": "2026-10-01T00:00:00+08:00"
  },
  "filters": [
    {
      "field_id": "f_status",
      "operator": "EQ",
      "value": "PAID"
    }
  ],
  "sort": [
    {
      "field_id": "mv_net_sales_4",
      "direction": "DESC"
    }
  ],
  "limit": 100,
  "timezone": "Asia/Shanghai"
}
```

### 5.8 AI 会话创建 · `POST /api/ai/conversations`

**请求 Schema：** `ConversationCreate`；**响应 data：** `Conversation`。**业务约束：** 权限和有效工作空间必须属于当前租户。

### 5.9 AI 问题提交 · `POST /api/ai/conversations/{conversationId}/messages`

**请求 Schema：** `AiQuestion`；**响应 data：** `TaskAccepted`。**业务约束：** 202 + task_id + events_url，Idempotency-Key 唯一；异步重放不重复扣次数。

```json
{
  "question": "最近30天各地区净销售额是多少？",
  "dataset_id": "dataset_01",
  "confirmed_context_version": 2
}
```

### 5.10 AI 任务恢复 · `POST /api/ai/tasks/{taskId}/resume`

**请求 Schema：** `AiResume`；**响应 data：** `AiTask`。**业务约束：** 仅 WAITING_CLARIFICATION 状态允许；需校验权限版本与确认上下文版本。

### 5.11 AI 证据 · `GET /api/ai/tasks/{taskId}/evidence`

**请求 Schema：** `无请求正文`；**响应 data：** `Evidence`。**业务约束：** 按最新权限检查 query_id、metric_version、generation、publish_seq，并避免输出禁列。

### 5.12 看板发布 · `POST /api/dashboards/{dashboardId}/publish`

**请求 Schema：** `GenericAction`；**响应 data：** `DashboardVersion`。**业务约束：** 原草稿提交版本；查看端只读取已发布版本。

### 5.13 报表导出 · `POST /api/exports`

**请求 Schema：** `ExportCreate`；**响应 data：** `TaskAccepted`。**业务约束：** 报告结果生成后按接收者实际权限二次校验，撤销后不能通过历史链接下载。

### 5.14 订单创建 · `POST /api/orders`

**请求 Schema：** `OrderCreate`；**响应 data：** `Order`。**业务约束：** 货币与金额服务端确定，支付以验签回调为准；同键重试不得重复下单。

## 6. AI SSE 流式事件、回放与取消

协议事件：`accepted → progress* → clarification? → result? / error? → done`；每事件带不可复用 `event_id`、单调 `seq`、`task_id`、`trace_id`、`emitted_at`。浏览器传 `Last-Event-ID=taskId:seq`，Java 按当前权限从持久化事件表重放；仅重连原任务，不重新创建付费任务。

```text
id: task_001:1
event: accepted
data: {"task_id":"task_001","seq":1,"status":"PENDING","trace_id":"trace_ai"}
id: task_001:2
event: progress
data: {"task_id":"task_001","seq":2,"stage":"semantic_retrieval"}
id: task_001:3
event: result
data: {"task_id":"task_001","seq":3,"result_ref":"result_01"}
id: task_001:4
event: done
data: {"task_id":"task_001","seq":4,"status":"SUCCEEDED"}
```

**关键新增数据库迁移：** 原 79 表基线不含 `ai_task_event`；需要新增序号、事件类型、脱敏 payload、tenant_id、task_id、retention_at、UNIQUE(tenant_id,task_id,seq)；在事件持久化机制正式验收前，不能宣称已支持断线补发。`POST cancel` 只是申请，工作进程确认停止数据库查询/Graph 后才显示 CANCELLED。

## 7. Java ↔ Python 内部调用安全与工具契约

| 方法 | 路径 | 调用方向 | 功能 |
|---|---|---|---|
| POST | `/internal/v1/ai/tasks/execute` | Java→Python | 创建或恢复 AI 编排任务 |
| POST | `/internal/v1/ai/tasks/{taskId}/cancel` | Java→Python | 通知 AI 服务取消任务 |
| POST | `/internal/v1/queries/validate` | Python→Java | 受控查询候选计划预检 |
| POST | `/internal/v1/queries/execute` | Python→Java | 二次鉴权并执行受控查询 |
| GET | `/internal/v1/semantic/authorized` | Python→Java | 获取已授权语义候选元数据 |

内部传输基线：mTLS/受控服务网络，短期委托 JWT/HMAC 双因子演进、带 `aud/exp/jti/task_id/permission_version/deadline_at/trace_id/attempt/body_hash`；服务端验证调用方与重放，不能直接信任模型返回的租户、角色或权限条件。内部服务原则上只传结构化计划与结果引用，不在 Python 保留大量明细。

**兼容说明：** 已有演示 `/api/ai/analyze` → `/internal/analyze` 与 `/internal/v1/queries/execute`（HMAC 时间戳）应通过适配器过渡到异步 `/ai/conversations/{id}/messages`；旧接口演示请求为 `{datasetId, question}`，与新契约的 `AiQuestion` 不同，不得直接替换请求体后宣称兼容。

## 8. 幂等、并发与最终一致性

| 类别 | 幂等/事务规则 |
|---|---|
| AI 提问 | `tenant + member + endpoint + Idempotency-Key + body_hash` 原子查重，回放原 task_id |
| 计费额度 | reserve→confirm/release；后台重试不重复扣计费请求；Token 逐供应商实际消耗对账 |
| 同步任务 | 同目标租约/隔离令牌；重复批次和事件安全重放，批次发布 CAS，失败补偿 |
| 导出 | 创建任务及写入对象按同一请求唯一键去重；每次取件二次鉴权 |
| 订单/回调 | 订单金额由服务端计算，供应商回调签名/时间窗口/事件 ID 验证后事务幂等 |
| 消息事件 | Outbox 至少一次投递，消费者 Inbox `event_id` 防重复处理 |

超时建议：元数据 500ms P95 目标、普通交互查询默认30s，AI 请求总时限60s；资源隔离为租户查询默认运行5/队列20。达到限额返回429/可见排队状态，绝不无限排队或无限重试。

## 9. 对外第三方 API 与 Webhook（P1）

租户级 API Key 必须由后台生成并限制资源范围、到期、吊销和速率；仅在完成租户授权与数据权限测试后对第三方开放。Webhook 采用 HMAC-SHA256 签名、时间戳、`event_id`、固定退避和消费者去重。支付回调只能由指定供应商接入端点完成，并根据真实供应商协议设计，不应纳入通用企业 API Key 的授权模式。本版 OpenAPI 仅描述自有对外 REST 与内部服务，第三方接入/支付回调待供应商确认后单独发布接口契约。

## 10. 可观测性、错误追踪与审计

记录：`trace_id / request_id / tenant_id / member_id / resource / action / elapsed_ms / quota_decision / permission_version / query_id / batch_id / model_usage`，日志脱敏。禁记录密码、完整 JWT、凭据、敏感明细或未经批准的 Prompt。审计写入追加模式。对登录风控、越权访问、SQL 拒绝、任务取消及支付回调均记录事件。

## 11. 接口一致性与自动化测试要求

| 测试类型 | 关键场景 | 验收标准 |
|---|---|---|
| Schema | OpenAPI YAML 可解析、`$ref` 全可解析、JSON 请求与响应示例匹配 | 100% 通过 |
| 认证 | JWT 过期、撤销、租户切换、旧刷新凭证重放 | 一律拒绝越权 |
| 租户隔离 | 两租户使用相同业务订单 ID；变更 ID/批量接口/AI/SSE/导出 | 零泄漏 |
| 幂等 | AI/同步/导出/订单同键同体回放、同键不同体 | 同体原任务、不同体409 |
| 版本 | If-Match 冲突、数据集发布后旧版查询、权限撤销缓存失效 | 无静默覆盖/越权旧结果 |
| 查询安全 | 禁列筛选排序、恶意 DSL、CTE/函数绕过、取消 | SQL 不越权、真实查询停止 |
| SSE | 断线补发、重复 event_id、跨租户读事件 | 顺序完整、无重复收费 |
| 支付 | 回调重复、验签失败、金额不一致 | 不重复生效 |
| 压测 | 100并发活跃用户与读/写/同步/AI混合 | 性能目标压测单独验收 |

## 12. API 实施阶段与变更治理

第一轮：落地现有八类基础端点兼容性测试、身份租户上下文与 `/queries`；第二轮：工作空间/权限/数据源/同步/语义及发布；第三轮：看板与完整 AI 异步会话、事件持久化；第四轮：P1 报告、告警、计费、模型网关、运营及第三方授权。每项接口必须对应 FR 编号、实现负责人、代码模块、测试编号及版本。

## 13. 设计未决项与落地边界

未确定的项目：支付供应商回调结构、企业 SSO/OIDC 方式、日志/事件持久化期限、Blob 存储供应商、支持模式数据范围、物理隔离套餐、数据处理区域、精确限流阈值等。应作为可配置及评审事项，不能擅自硬编码为客户合同条款。

## 14. 对外 URL 去版本号专项迁移方案（V1.1）

### 14.1 新旧路由策略

| 类型 | 原路径或配置 | 新版统一规范 | 适用条件 |
|---|---|---|---|
| Vue3 / Spring Boot 对外 REST | `/api/v1/...` | `/api/...` | 所有公网或浏览器业务 API |
| AI 对外业务接口 | `/api/v1/ai/...` | `/api/ai/...` | 经 Java 接入网关；不得让浏览器直连 FastAPI |
| Java ↔ Python 内部接口 | `/internal/v1/...` | `/internal/v1/...`（不变） | 仅私网 + 短期委托 + 强鉴权 |
| 接口文档版本 | OpenAPI `1.0.0` | OpenAPI `1.1.0` | 文档版本不等于运行时路由版本 |
| 旧项目演示端点快照 | `contracts/openapi-legacy-starter.yaml` | `contracts/openapi-legacy-starter.yaml` | 仅存档兼容参考，不代表已迁移 |

**目标路由举例：** `POST /api/auth/login`、`GET /api/datasets`、`POST /api/queries/plans/execute`、`POST /api/ai/conversations/{conversationId}/messages`、`GET /api/ai/tasks/{taskId}/events`。所有新版接口均以正式 `openapi-public.yaml` 为唯一来源。

### 14.2 基础工程六项演示 API 对照

| 旧实现路径 | 旧请求/返回 | 新目标路径和约定 | 迁移措施 |
|---|---|---|---|
| `POST /api/v1/auth/login` | `{email,password,tenantId}` → 原始 Token Map | `POST /api/auth/login` → 统一 Envelope、LoginRequest | DTO 适配器 + 新旧契约测试 |
| `GET /api/v1/me` | 原始 `{user_id,tenant_id,member_id}` | `GET /api/me` → UserInfo/Envelope | 重新封装用户权限快照 |
| `GET /api/v1/workspaces` | 原始数组 `{id,name}` | `GET /api/workspaces` → 分页结果 | 前端分页转换，避免对象/数组类型混淆 |
| `GET /api/v1/datasets` | 原始数组 | `GET /api/datasets` → 资源 DTO/分页 | 数据可见性和行列权限回归 |
| `POST /api/v1/queries` | `{datasetId,metric,groupBy}` → 直接聚合 | `POST /api/queries/plans/execute` → QueryPlan/QueryResult | 保留兼容适配，生产受控计划由 Java 编译 |
| `POST /api/v1/ai/analyze` | `{datasetId,question}` → 同步答案 | `POST /api/ai/conversations/{conversationId}/messages` → 异步 TaskAccepted/SSE | 旧演示分支与新异步任务链路并行后切换 |

### 14.3 代码调整涉及的配置

- **Spring Boot**：外部 `@RequestMapping`、鉴权白名单、全局异常响应、OpenAPI 路由按 `/api` 更新；内部 `/internal/v1` 独立，不进行全局文本替换。应采取资源 Controller 级改动及契约测试，而非 Nginx 任意 Rewrite 造成安全检查遗漏。
- **Vue3**：统一 API client 的 baseURL 更新为 `/api`，避免在组件中硬编码 `/v1`；请求和响应 DTO 与新版契约同步。租户切换清理旧缓存、SSE。
- **Nginx / Ingress**：`location /api/` 继续透传 Spring Boot，确认 `/api/auth/login` 不被重复拼接路径；内部路由不得开放公网。
- **文档与测试**：只允许 `openapi-legacy-starter.yaml` 和本迁移章出现旧的 `/api/v1` 文本；CI 阻止目标接口重新引入 `/api/v1`。
- **部署次序**：先提供带兼容层的 Java 路由 → 新旧集成与权限回归 → 前端切换 `/api` → 观察失败/流量 → 按客户迁移窗口有审计地撤下旧路由。

### 14.4 不兼容修改与未来版本治理

非破坏性字段扩展继续使用同一路径并提升文档次版本。不兼容变更不能直接改变已有响应；未来如确有多代客户端并存需要，可选择服务端支持的 `Accept: application/vnd.aibi.v2+json` 等媒体类型协商，或建立专用过渡路由，并在 OpenAPI 中分别定义。**该媒体类型示例属于治理预案，当前接口不声明已经实现内容协商。**

**迁移验收：** 目标 149 条对外路径统一以 `/api` 为根（与服务器 URL 合成后），全部接口路径不含 `/v1`；5 条内部路由保持 `/internal/v1`；旧客户端若处于迁移期，可继续调用原路由且不泄漏租户数据；最终新客户端只访问新版路由。

## 附录 A. 交付文件索引

- `contracts/openapi-public.yaml`：目标对外端点 OpenAPI 3.1.1 草案。
- `contracts/openapi-legacy-starter.yaml`：现有六项演示端点与原始请求响应形状。
- `contracts/openapi-internal-v1.yaml`：保留 `/internal/v1` 的 Java/Python 内部端点草案。
- `接口清单.csv`：每条 API、实现状态、权限/幂等与阶段。
- `examples/`：请求、响应、错误、SSE 和证据示例。
- `tests/validate_contracts.py`：静态一致性校验脚本。

## 附录 B. 文档来源和协议依据

- 项目已有六份需求、架构、数据库、工程和 AI/稳定性文档（本交付包从上述已有内容承接）。
- OpenAPI Specification 3.1.1：https://spec.openapis.org/oas/v3.1.1.html
- MDN Server-sent events：https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
