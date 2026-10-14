# 统一数据查询引擎验收说明（任务 98—111）

## 交付范围

本阶段实现了传统 BI 与后续 AI 工具共用的 Java QueryService。浏览器和模型都只能提交强类型 `QueryPlan`，不能提交 SQL、租户身份、角色或数据库对象名；可信租户、成员、权限版本、语义版本和数据发布指针均由服务端补全。

| 任务 | 状态 | 实现摘要 |
|---|---|---|
| 98 | 完成 | `schema/query-plan.schema.json` 与 Jakarta Validation 双层约束指标、维度、筛选、排序、行数和未知字段。 |
| 99 | 完成 | 查询只接受当前 `PUBLISHED` 数据集版本和指标版本，并校验工作空间、数据集及允许维度。 |
| 100 | 完成 | `PhysicalMappingManager` 只从发布元数据映射受控 DWD 表和物理列，标识符必须通过白名单。 |
| 101 | 完成 | `QueryPlanCompiler` 从闭合 AST 生成单条只读 `SELECT`，只支持批准的聚合、算术、比较和日期粒度。 |
| 102 | 完成 | 每个事实扫描在最内层固定注入 `tenant_id` 和 `dataset_id` 参数，候选计划无法覆盖。 |
| 103 | 完成 | RLS 同组 OR、异组 AND；CLS 拒绝列不能读取，掩码列不能参与筛选、时间条件、排序或指标公式。 |
| 104 | 完成 | 所有值使用 JDBC 参数；请求没有 SQL 字段，编译结果拒绝多语句、系统表和外部表函数。 |
| 105 | 完成 | 执行前固定读取 `bi_dataset_publication`，ClickHouse 扫描限定 `active_generation` 与 `committed_publish_seq`。 |
| 106 | 完成 | 先过滤发布可见性，再按 `(source_id,business_key)` 用 `argMax(source_version,publish_seq,row_event_key)` 选有效版本，最后排除删除事件。 |
| 107 | 完成 | 公平信号量限制全局及租户并发，租户等待队列有界，JDBC 和服务层均有超时。 |
| 108 | 完成 | 服务端生成并持久化 `query_id`；执行器记录活动 JDBC Statement，取消请求调用数据库驱动实际 `cancel()`。 |
| 109 | 完成 | 缓存键包含租户、成员、双层权限版本、策略哈希、语义版本、发布代/序号、时区和计划哈希。 |
| 110 | 完成 | `bi_query_task` 记录生命周期和错误分类，`sys_audit_event` 保存摘要、耗时、行数、取消状态及 SQL 摘要，不记录明细参数。 |
| 111 | 完成 | 新增编译器租户注入、业务键版本选择、参数绑定、物理列注入、RLS/CLS 掩码、Raw SQL 拒绝和队列上限测试；既有金额、跨租户与权限版本测试继续回归。 |

## API 与前端

- `POST /api/queries` 与 `POST /api/queries/plans/execute`：执行查询。
- `POST /api/queries/async`：立即返回 query_id，供状态轮询和并发取消。
- `POST /api/queries/validate`：只做版本、权限和安全编译校验。
- `GET /api/queries/{queryId}`、`GET /result`、`POST /cancel`：状态、结果和实际取消。
- 前端新增“统一查询”入口，可选择发布版本、指标、维度、筛选、排序与行数，并展示发布代、序号和 query_id。

## 安全顺序

```text
候选 QueryPlan
  -> 当前租户/成员与权限版本
  -> 当前发布语义版本
  -> RLS/CLS 决策
  -> generation/publish_seq 快照
  -> 参数化 SQL AST
  -> 业务键有效版本
  -> RLS 与候选筛选
  -> 聚合/排序/限量
  -> 完成前二次鉴权
  -> 权限隔离缓存与审计
```

真实 ClickHouse 数据验收仍要求环境中的 DWD 表与已发布数据集物理字段映射一致，并使用只读 `aibi_query_role`；单元测试不替代生产账号权限和真实取消链路的环境验收。
