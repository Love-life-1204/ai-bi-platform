# AI 智能分析引擎验收说明

## 1. 交付边界

浏览器只访问 Spring Boot。Java 负责会话、授权快照、任务状态、事件账本、SSE 和最终数据查询；FastAPI 只接受 HMAC 短时委托，使用 LangGraph 编排语义检索、澄清、计划、预检、查询、校验、图表和回答。AI 服务不直连 MySQL 或 ClickHouse。

本轮完成 124—143，共 20 项。第 143 项按阶段验收口径验证中文分析主链路、断线恢复、动态撤权和工具重试幂等。

## 2. 验收矩阵

| 编号 | 状态 | 实现 |
|---|---|---|
| 124 | 完成 | `ai-service/app` 按 api、graph、tools、schemas、providers、persistence、services、security、core 分层。 |
| 125 | 完成 | 严格 Pydantic 请求模型、DelegatedContext、QueryPlan、ChartSpec、Answer 和 GraphState；未知字段一律拒绝。 |
| 126 | 完成 | Java 持久化 AI 会话、消息、确认上下文版本和乐观版本检查。 |
| 127 | 完成 | admission 校验受众、工具范围、可用工具额度、委托有效期、任务 deadline 与取消标记；Java 在入口先校验成员和 `ai:ask`。商业套餐请求额度仍由后续 SaaS 配额模块接入。 |
| 128 | 完成 | intent 节点输出意图和置信度，模型异常转换为稳定错误码。 |
| 129 | 完成 | FastAPI 仅通过签名 Java 工具检索当前任务已授权的数据集、已发布指标与业务术语。 |
| 130 | 完成 | 多指标或口径不确定时调用 LangGraph `interrupt`，返回授权候选，不接受候选外版本。 |
| 131 | 完成 | plan 节点将确认口径转换为字段 ID/版本 ID 驱动的 QueryPlan，不生成 SQL。 |
| 132 | 完成 | Pydantic 强 Schema 校验后调用 Java `queries/validate`；Java 再做权限、版本、RLS/CLS 和查询安全校验。 |
| 133 | 完成 | Java/FastAPI 双向 HMAC-SHA256 签名，包含 method、path、timestamp、nonce、body hash；防过期和重放。 |
| 134 | 完成 | result_check 校验行数、数值类型和有限数；查询结果携带发布代、提交序号与质量版本。 |
| 135 | 完成 | 按返回字段生成受限 KPI/BAR/LINE ChartSpec，前端只映射受控字段到 ECharts。 |
| 136 | 完成 | Answer 强制分离 facts、assumptions、recommendations、limitations；事实携带 query_id 和指标版本。 |
| 137 | 完成 | 本地使用 SQLite Checkpointer；生产配置强制 Redis Checkpointer。 |
| 138 | 完成 | checkpoint thread 固定、interrupt/resume、启动扫描非终态任务、Python 本地工具账本与 Java 权威工具调用账本双层幂等、Java 事件序号去重。 |
| 139 | 完成 | ACCEPTED/RUNNING/WAITING_CLARIFICATION/CANCEL_REQUESTED/终态管理；Java deadline 兜底并产生可恢复 error/done 事件。 |
| 140 | 完成 | Java SSE 支持 `Last-Event-ID`、持久事件回放、断线重连和终态关闭。内部工具端点从公共 OpenAPI 隐藏。 |
| 141 | 完成 | Vue 提供会话、问题、澄清、进度、取消、图表、事实/假设/建议/限制和证据界面；切换租户会卸载旧流。 |
| 142 | 完成 | 自动测试覆盖签名篡改/重放/过期、提示词注入额外字段、无权限语义、模型失败、checkpoint 恢复和工具只执行一次。 |
| 143 | 完成 | 中文问题验收覆盖意图、授权目录、澄清恢复、查询、图表及带 `query_id` 的事实解释；SSE 用 `Last-Event-ID` 恢复；缓存结果返回前重新鉴权；工具调用键持久化并与签名请求体绑定，响应丢失后的重试不会再次执行查询或产生第二个计费事件。 |

## 3. 恢复与安全不变量

- 委托上下文只由 Java 生成；FastAPI 不能扩大 tenant、member、workspace、版本或工具范围。
- 每次实际查询都由 Java 使用任务授权快照重新鉴权；撤权、会话失效或权限版本变化立即拒绝。
- QueryPlan 只能引用授权目录中的不可变 ID，Java 工具不接受模型生成 SQL。
- `(tenant_id, ai_task_id, sequence)` 保证事件可去重；SSE ID 为 `{taskId}:{sequence}`。
- 工具幂等键绑定任务、调用序号、工具名和参数哈希，并同时写入 HMAC 签名请求体和 `Idempotency-Key`；相同键不同参数直接失败。
- Java 在执行查询前原子占有 `ai_tool_invocation`，成功结果先持久化再响应；响应丢失时重试读取同一调用结果，不重复执行。计费以同一工具调用/供应商请求 ID 为业务事件，数据库唯一键拒绝重复流水。
- 已缓存结果也必须先通过当前会话和权限版本重新鉴权；权限撤销后既不重新执行，也不返回旧结果。
- AI 服务重启后扫描 ACCEPTED、RUNNING、CANCEL_REQUESTED 任务并从持久 checkpoint 继续；WAITING_CLARIFICATION 保留到用户恢复。
- 生产环境缺少 Redis Checkpointer 或弱共享密钥时拒绝启动。

## 4. 数据库升级

`V004__ai_engine_recovery.sql` 新增上下文版本、任务尝试/事件游标、AI 消息任务引用、授权快照、会话上下文和可恢复事件表。既有 `mysql.sql` 基线保持不变，避免已安装环境因校验和变化无法前滚；最终对象清单由基线和有序迁移共同计算。

## 5. 已执行验证

```powershell
# AI 服务
cd ai-service
.\.venv\Scripts\python.exe -m pytest

# Java
cd backend
mvn -q test

# Vue
cd frontend
npm run ci
npm run build

# 随机临时 MySQL 库，执行后自动清理
ai-service\.venv\Scripts\python.exe infrastructure\tests\test_initialization.py
```

当前结果：AI 服务 8/8、前端 28/28、Spring Boot 80 个测试中 79 通过且 1 个既有用例跳过、数据库初始化 2/2；Vue 生产构建通过。构建仅提示 ECharts 相关 chunk 大于 500 kB，不影响功能，可在后续性能优化中进一步拆包。

第 143 项的关键自动化证据：

- `test_interrupt_resume_uses_checkpoint_and_executes_tool_once`：中文问题识别为 `ANALYZE`，澄清恢复后只查询一次，并生成 KPI ChartSpec、可追溯事实和分离的建议/假设/限制。
- `PermissionVersionReauthorizationIntegrationTest`：权限版本变化使任务授权快照立即失效。
- `AiInternalToolIdempotencyTest`：相同调用键只执行一次；读取 Java 缓存前仍重新鉴权，撤权时拒绝返回旧数据。
- `test_lost_response_retry_reuses_authoritative_tool_call`：模拟 Java 已提交但 HTTP 响应丢失，重试沿用相同签名工具调用键并恢复结果。
- `sse.test.ts`：断线续连发送持久化的 `Last-Event-ID`，终态事件后停止连接。

## 6. 启动配置

Java 与 FastAPI 必须配置相同的高强度随机共享密钥：

```text
AI_SERVICE_URL=http://127.0.0.1:8091
AI_SHARED_SECRET=<至少 32 个随机字符>
AI_JAVA_BASE_URL=http://127.0.0.1:8080
```

生产 AI 服务还需：

```text
AI_ENV=production
AI_CHECKPOINT_BACKEND=redis
AI_CHECKPOINT_REDIS_URL=redis://...
```

部署前先应用 MySQL V004，再启动 Java 和 FastAPI。不要让浏览器或公网直接访问 `/internal/v1/**`。
