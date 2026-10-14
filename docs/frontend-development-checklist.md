# 前端现状审查与开发清单

审查日期：2026-10-13  
审查范围：`frontend/`、前端直接依赖的 Spring Boot Controller、`ai-service/` 及现有接口/架构文档。

## 1. 阶段结论

- 当前 `frontend` 可以安装依赖、通过 TypeScript 严格检查、单元测试和 Vite 生产构建，并可由开发服务器返回入口页和主模块。
- 第二阶段已建立 Vue 3 应用入口、Vue Router、Pinia、Element Plus、公共 Layout；现有业务原型通过路由适配页渐进接入。
- 第三阶段已完成登录、全局会话、租户上下文、令牌刷新、退出、401/403 处理与路由守卫，并直接对接 Spring Boot 身份接口。
- 第四阶段已完成企业工作台、工作空间切换、企业信息、部门成员、邀请与角色分配；企业/工作空间切换会取消旧请求并清理缓存、图表页面和 AI 对话上下文。
- 第五阶段已完成原生 Vue 数据源管理页：数据库与文件数据源、连接测试、元数据浏览、源表选择、依赖提示、停用和删除均已接入实际接口。
- 第六阶段已完成文件导入与数据同步工作台：字段映射、异步进度、全量/增量任务、批次质量、失败重试和取消均使用真实后端状态。
- 第七阶段已完成原生 Vue 数据集管理页：列表、四步创建向导、字段语义、关联风险、权限预览、错误定位、草稿、校验、发布和版本历史均已接入真实接口。
- 第八阶段已完成原生 Vue 指标管理页：多条件筛选、四步向导、受控公式构建、权限样例试算、时间/币种治理、校验定位、发布、版本和依赖影响均已落地。
- 第九阶段已完成原生 Vue 图表设计器：七类图表、发布指标选择、字段绑定、显示配置、实时查询预览、编辑、校验、发布和历史版本均已落地。
- 第十阶段已完成原生 Vue 看板管理页：分类列表、12 列画布、组件增删/拖拽/尺寸调整、全局与联动筛选、权限下钻、草稿预览、发布、版本及局部失败均已落地。
- 第十一阶段已完成原生 Vue AI 智能分析工作台：会话历史、范围选择、任务状态、SSE 恢复、三类澄清、事实/假设/建议、安全图表、查询证据、停止重试与反馈均已落地。
- 第十二阶段已完成 P1 商业化页面：报告/导出/订阅、告警、套餐/额度/订单、MFA/SSO/模型策略和平台运营控制台均已接入真实接口。
- 已有数据源、同步、语义数据集/指标、统一查询、图表看板五个业务域的原型和 API 封装，可作为 Vue 化时的业务逻辑与接口类型基线。
- 登录页已完成，可选择企业、校验输入、防止重复提交，并展示认证及会话过期错误。
- 图表看板已有功能原型、类型、API 封装及 ECharts 渲染层，并已接入 `/dashboards` 懒加载路由；业务主体仍待按域迁移为原生 Vue 组件。
- AI 工作台、前端 API/SSE 封装、Spring Boot 聚合接口和 LangGraph AI 服务均已实现；任务恢复和证据读取会重新鉴权。

## 2. 工程与文件清单

### 2.1 入口和工程配置

| 文件 | 用途 | 现状 |
|---|---|---|
| `frontend/index.html` | SPA HTML 入口 | 已有，加载 `/src/main.ts` 和统一 CSS |
| `frontend/src/main.ts` | Vue 应用装配 | 已有，挂载 Vue、Router、Pinia 和按需 Element Plus 组件 |
| `frontend/src/layouts/AppLayout.vue` | 后台公共布局 | 已有，统一侧栏、顶部栏、页面标题和路由内容区 |
| `frontend/vite.config.ts` | Vite 配置及 `/api` 开发代理 | 已有，代理到 `http://127.0.0.1:8080` |
| `frontend/tsconfig.json` | TypeScript 严格配置 | 已有，开启 strict、noUncheckedIndexedAccess、exactOptionalPropertyTypes |
| `frontend/package.json` | 脚本和依赖 | 已有；开发、检查、测试、构建脚本可执行 |
| `frontend/src/datasources/datasources.css` | 全局及所有业务页样式 | 已有，但样式与单一业务目录耦合，应在 Vue 化时拆分 |

### 2.2 页面清单

当前共有 **33** 个 `.vue` 文件。以下原生 TypeScript 页面原型继续作为业务实现，并已由 `src/views/legacy/*.vue` 适配到统一 Layout：

| 业务页面/视图 | 实现文件 | 现状 | 主要能力 |
|---|---|---|---|
| 应用壳与导航 | `src/main.ts` | 已有原型 | 五个业务入口的按钮式切换，默认进入数据源 |
| 数据源管理 | `src/views/legacy/DatasourcePage.vue` | 已完成 | MySQL/PostgreSQL/文件数据源、连接测试、元数据、源表选择、依赖提示、停用和删除 |
| 文件导入与数据同步 | `src/views/legacy/SyncPage.vue` | 已完成 | CSV/XLSX、预览/映射、异步导入、全量/增量任务、批次质量、取消与失败补偿 |
| 数据集管理 | `src/views/legacy/SemanticPage.vue` | 已完成 | 数据集列表、创建/编辑向导、字段语义、关联、权限预览、校验定位、发布与版本历史 |
| 指标管理 | `src/views/legacy/MetricPage.vue` | 已完成 | 指标筛选、创建/编辑向导、受控 AST、试算、业务规则、校验、发布、历史版本及依赖影响 |
| 统一查询/结果 | `src/query/query-page.ts` | 已有原型 | QueryPlan 表单、筛选 JSON、同步执行、结果表格 |
| 图表设计器 | `src/views/ChartDesignerPage.vue` | 已完成 | 图表筛选、七类组件、字段和格式配置、实时预览、草稿编辑、校验、发布与历史版本 |
| 看板管理 | `src/views/DashboardManagementPage.vue` | 已完成 | 分类列表、12 列编排、尺寸与位置保存、筛选联动、权限下钻、独立状态、发布和历史版本 |
| 登录 | `src/views/LoginPlaceholderPage.vue` | 已完成 | 邮箱、密码、企业选择、校验、密码显隐、错误状态与防重复提交 |
| 企业工作台 | `src/views/WorkspaceHomePage.vue` | 已完成 | 授权指标、最近看板、同步任务与工作空间摘要 |
| 企业信息 | `src/views/CompanySettingsPage.vue` | 已完成 | 企业名称、时区、币种和乐观锁更新 |
| 组织与成员 | `src/views/OrganizationPage.vue` | 已完成 | 部门树、成员、邀请、角色分配与授权状态 |
| AI 智能分析 | `src/views/AiAnalysisPage.vue` | 已完成 | 会话历史、授权范围、完整任务状态、SSE 恢复、三类澄清、分区回答、安全图表、证据、停止重试与反馈 |
| 报告与交付 | `src/views/ReportsPage.vue` | 已完成 | 报告详情、三类异步导出、状态轮询、取消/下载和定时收件订阅 |
| 告警中心 | `src/views/AlertsPage.vue` | 已完成 | 规则、抑制窗口、事件证据、确认/恢复/抑制状态 |
| 套餐与账单 | `src/views/BillingPage.vue` | 已完成 | 套餐对比、订阅到期、额度用量、订单和服务端支付确认 |
| 企业安全 | `src/views/EnterpriseSecurityPage.vue` | 已完成 | MFA、OIDC SSO、模型供应商和数据策略 |
| 平台运营 | `src/views/PlatformOperationsPage.vue` | 已完成 | 独立平台令牌、租户/订阅/资源聚合及服务健康 |

### 2.3 组件清单

第二阶段已新增 Layout、导航、页面标题、表格、分页、搜索、表单、对话框和统一状态 Vue 组件。以下既有模块继续作为业务组件候选：

| 模块 | 文件 | 可复用内容 |
|---|---|---|
| 图表渲染器 | `src/dashboard/chart-view.ts` | KPI、明细/透视表、柱/折/饼/堆叠图、空态/错误态、大结果抽样、ResizeObserver、点击联动与下钻 |
| 连接测试状态映射 | `src/datasources/connection-status.ts` | 成功、认证失败、超时、权限不足、安全策略等错误文案映射 |

后续仍可按复杂度拆分 `DatasourceForm`、`MetadataBrowser`、`ChartRenderer`、`DashboardGrid`、`AiMessageList` 和 `AiComposer` 等业务组件。

### 2.4 路由清单

- Vue Router：已安装并启用。
- 路由配置：`src/router/index.ts`。
- 当前导航：`/workspace`、`/datasources`、`/sync`、`/semantic`、`/metrics`、`/query`、`/dashboards`、`/ai`、`/organization/company`、`/organization/members`、`/design-system` 均支持深链、刷新恢复、懒加载和侧栏高亮；父级路由统一启用认证守卫，未登录跳转 `/login`。

建议的 P0 路由：

| 路径 | 页面 | 守卫 |
|---|---|---|
| `/login` | 登录 | 仅游客 |
| `/` | 工作台/重定向 | 已认证 |
| `/datasources`、`/datasources/new`、`/datasources/:id/edit` | 数据源 | `DATASOURCE_*` |
| `/sync`、`/sync/:runId` | 同步任务与运行详情 | `SYNC_*` |
| `/semantic/datasets`、`/semantic/metrics` | 数据集与指标 | `SEMANTIC_*` |
| `/query` | 统一查询 | `BI_QUERY` |
| `/charts`、`/dashboards`、`/dashboards/:id` | 图表与看板 | `DASHBOARD_*` |
| `/ai`、`/ai/conversations/:id` | AI 分析 | `AI_QUERY` |

### 2.5 状态管理清单

- Pinia：`src/stores/app.ts` 管理布局、全局会话、当前用户/成员、企业、工作空间、刷新并发合并和通知状态。
- 当前全局状态：由登录、成员关系、企业切换及工作空间接口初始化，并在刷新失败或退出时集中清理。
- 当前页面状态：保存在各页面类实例字段中；切页即销毁，没有跨路由缓存。
- 企业与工作空间切换已统一取消旧请求、清除业务缓存和 AI 事件游标，并通过上下文版本重建路由页；后续状态重点为细粒度权限菜单和全局 Loading 可视化。

后续仍需建立独立 `auth` 与 `permission` Store；业务大结果不长期放入 Pinia。

### 2.6 API 文件清单

| 文件 | 已封装接口范围 |
|---|---|
| `src/api/client.ts` | `/api` 根路径、响应信封、Bearer/租户/成员/幂等/Trace 请求头、超时与主动取消、统一错误、并发 Loading 计数 |
| `src/api/types.ts` | `ApiResponse`、字段校验错误类型与响应守卫 |
| `src/auth/api.ts` | 登录、刷新、退出、成员关系、企业切换、企业详情与工作空间 |
| `src/api/organization.ts` | 企业信息、成员、部门、角色、成员角色分配与邀请 |
| `src/datasources/api.ts` | 数据源列表/创建/更新/停用/删除、连接测试、元数据、依赖检查、文件上传与预览 |
| `src/sync/api.ts` | 文件导入任务、同步任务创建/编辑/启停、运行详情、取消、手动执行与失败补偿 |
| `src/semantic/api.ts` | 数据集与指标的列表/创建/读取/更新/校验/发布、版本历史及数据集权限预览 |
| `src/query/api.ts` | `POST /queries/plans/execute` 同步查询 |
| `src/dashboard/api.ts` | 图表和看板的列表/创建/更新/校验/发布/查询 |

缺失的 P0 API 模块：细粒度权限菜单能力。`ApiClient` 已支持两级令牌、401 单次刷新与请求重放、403 跳转、204 空响应，以及企业/工作空间切换时的请求集中取消。

## 3. 依赖核对

| 技术 | 当前配置 | 结论 |
|---|---|---|
| Vue 3 | `3.5.43` | 第二阶段已安装并作为应用入口 |
| TypeScript | `5.9.3` | 与 `vue-tsc` 兼容，严格检查可通过 |
| Vite | `8.3.4` | 已安装，开发与生产构建可用 |
| Pinia | `4.0.3` | 第二阶段已安装，用于应用级布局和租户状态 |
| Element Plus | `2.14.7` | 第二阶段已安装并封装公共组件 |
| ECharts | `5.6.0` | 已安装并用于图表渲染 |
| Vue Router | `5.4.0` | 已建立正式路由、路由高亮和页面懒加载 |
| `@vitejs/plugin-vue` | `6.0.10` | 已启用 `.vue` 编译 |
| `vue-tsc` | `3.3.13` | 已纳入 `npm run check` 类型门禁 |

第二阶段已完成 Vue 工程骨架、公共 Layout、设计令牌和基础组件；原生业务页面通过 Vue 路由适配层接入统一 Layout，后续可按业务域渐进迁移，避免两套 API 与类型定义。

## 4. 安装、检查、测试与运行结果

| 命令/检查 | 结果 |
|---|---|
| `npm install` | 通过；依赖已是最新，无安装错误 |
| `npm run check` | 初次失败；修复后通过 |
| `npm run ci` | 通过；13 个测试文件、64 个测试全部通过 |
| `npm run build` | 通过 |
| `npm run dev` | 修复配置加载后启动成功，入口 `http://localhost:5173/` 返回 200 |
| 入口页检查 | HTML 标题和 `#app` 存在，`/src/main.ts` 返回 200 |

本阶段修复：

1. 为 TypeScript 启用 `skipLibCheck`，隔离 ECharts 5.6 声明文件与 `exactOptionalPropertyTypes` 的第三方兼容问题，同时继续严格检查项目源码。
2. 将 ECharts 实例和 `ResizeObserver` 字段显式声明为 `T | undefined`，修复项目源码严格类型错误。
3. 将 `dev` 脚本改为 `vite --configLoader native`，避免 Windows 受限环境中默认配置加载器触发 `spawn EPERM`。

当前非阻断警告：看板异步块约 536 kB（gzip 约 178 kB），超过 Vite 500 kB 提示阈值；主入口已降至约 37 kB。Vue 化后仍应按路由动态导入，并进一步细拆 ECharts 渲染依赖。

## 5. 登录、看板与 AI 页面复用分析

### 5.1 登录

状态：**已完成**。

可复用：

- `ApiClient` 的 Bearer Token 注入、错误规范化、Trace、超时和取消。
- 后端已存在 `/api/auth/login`、`/refresh`、`/logout`、`/switch-tenant` 基础接口。

已实现：两级令牌、Pinia 当前成员上下文、租户/工作空间装载、401 刷新并发合并与单次请求重放、刷新失败回登录页、403 无权页、服务端退出及 204 响应支持。安全流程说明见 `docs/frontend-authentication.md`。

### 5.2 图表与看板

状态：**图表设计与看板管理均已完成原生 Vue 化**。

建议直接保留：

- `dashboard/types.ts` 的图表/看板/布局/筛选 DTO 和字段绑定校验。
- `dashboard/api.ts` 的接口路径和输入输出边界。
- `chart-view.ts` 的安全声明式 ECharts 映射、空态/局部错误态和交互逻辑；迁移为 Vue 组件生命周期。

建议作为行为参考后重写：

- `dashboard-page.ts` 的列表、创建、发布、草稿预览、查询、筛选、联动和拖拽交互。
- 当前原生 DOM 拼接、`alert` 和 HTML5 简单拖放不适合作为最终企业级 Vue 页面。

图表设计器已完成：编辑已有图表、历史版本、查询取消/乱序保护、单组件错误隔离、独立路由拆包，以及无数据、零值、异常值和大结果预览处理。

看板已完成：布局移动与尺寸调整、权限结果下钻、后台标签暂停自动刷新、最短 60 秒有界刷新、按组件错误隔离和移动端单列只读布局。

### 5.3 AI 演示/对话

状态：**第十一阶段已完成**。

- `AiAnalysisPage.vue` 已提供持久化会话、历史消息、工作空间/数据集范围、完整任务轨迹和停止/重试/反馈。
- `ai/api.ts`、`ai/sse.ts` 已封装任务请求、幂等提交、SSE 断线恢复、任务恢复、证据与反馈，测试覆盖事件解析。
- 指标、时间和业务口径澄清共用强类型组件；事实、假设、建议和限制分区展示，无数据时不生成虚假图表。
- `ai/presentation.ts` 将模型 `ChartSpec` 映射为前端白名单图表，并集中处理超时、模型不可用、取消、无权限和权限撤销状态。
- 查询证据展示指标版本、数据版本、查询时间、发布版本和追踪 ID；AI 服务已补充相应证据字段。
- 企业或工作空间切换会删除 AI 事件游标；路由组件重建时页面卸载并中止 SSE、释放图表实例。

后续增强项：任务历史服务端分页、更细粒度的 AI 能力权限菜单，以及时间/业务歧义在 AI 编排层的更多真实触发策略。

## 6. 后续页面开发清单

状态定义：**已有原型**＝可复用业务逻辑但须迁移 Vue；**待完善**＝已有主体但缺少生产能力；**缺失**＝仓库无页面实现。

| 优先级 | 页面/能力 | 状态 | 依赖接口/前置条件 |
|---|---|---|---|
| P0 | Vue 应用骨架、布局、主题、错误边界 | 已完成 | Vue、Router、Pinia、Element Plus、Vue 插件与 `vue-tsc` 已落地 |
| P0 | 登录、刷新、退出 | 已完成 | `/api/auth/login`、`/refresh`、`/logout` 已接入并测试 |
| P0 | 租户/工作空间上下文 | 已完成 | `/api/auth/switch-tenant`、memberships、workspaces 已接入；细粒度权限菜单待后续接口 |
| P0 | 数据源列表与新增/编辑 | 已完成 | `/api/datasources*` 已接入 Vue + Element Plus 页面，凭据不回显 |
| P0 | 同步任务与运行详情 | 已完成 | `/api/sync-jobs*`、`/api/sync-runs*` 已接入创建/编辑、启停、真实进度、取消与失败补偿 |
| P0 | 文件上传、预览与异步导入 | 已完成 | `/api/files*`、`/api/imports*` 已接入字段映射、严格/容错模式和真实任务终态 |
| P0 | 数据集管理 | 已完成 | `/api/datasets*` 已接入 Vue 向导、更新、校验定位、权限预览、发布和版本历史 |
| P0 | 指标管理 | 已完成 | `/api/metrics*` 已接入 Vue 向导、受控公式、权限样例试算、校验、发布、版本历史及依赖影响 |
| P0 | 统一查询 | 已有原型 | `/api/queries*` 已有；补异步查询、取消、状态和结果分页 |
| P0 | 图表设计 | 已完成 | `/api/charts*`、`/api/queries/plans/execute` 已接入编辑、校验、版本历史、实时预览与发布 |
| P0 | 看板列表/编辑/查看 | 已完成 | `/api/dashboards*` 已接入网格编辑、全局/联动筛选、刷新治理、局部错误、发布与角色权限查询 |
| P0 | AI 智能分析 | 已完成 | AI service、Java 聚合 API、会话/消息/任务/SSE/澄清/证据/反馈接口与前端工作台均已接入 |
| P0 | 企业工作台与工作空间切换 | 已完成 | 授权指标、看板、同步 API 与安全上下文清理已接入 |
| P0 | 组织成员、角色与邀请 | 已完成 | 新增企业成员列表契约；部门、角色、邀请与成员角色接口已接入 |
| P1 | 报告、导出与订阅 | 已完成 | 报告详情、异步导出终态、鉴权下载、定时调度和成员收件已接入 |
| P1 | 告警中心 | 已完成 | 规则、事件、抑制、确认、恢复状态和证据详情已接入 |
| P1 | 套餐、用量与账单 | 已完成 | 套餐、订阅、额度、Token 用量、订单及服务端支付确认已接入 |
| P1 | 审计日志 | 服务端已完成 | `/api/audit-events` 已实现；待补筛选与导出页面 |
| P1 | 平台运营控制台 | 已完成 | 独立平台凭据、运营聚合指标、资源与基础服务健康已接入 |

## 7. 推荐实施顺序与防重复约束

1. 先安装 Vue 技术栈并建立 `App.vue`、Router、Pinia、布局、鉴权守卫和统一错误/Loading，不创建重复 API Client。
2. 先完成登录与租户/工作空间上下文，再迁移业务页面，避免每页继续直接读取 `localStorage`。
3. 按数据源 → 同步/导入 → 语义 → 查询 → 图表看板顺序，将现有 API/类型保留并逐步迁移页面；每迁移一个域就删除对应原生 DOM 入口，避免两套实现并存。
4. AI 页面已按会话、任务状态和 SSE 契约实现；后续扩展继续保持真实查询证据和禁止前端假数据的约束。
5. 所有新增路由采用动态导入；图表域单独拆包。租户切换和退出必须取消请求/SSE并清空租户级缓存。
6. CI 至少执行 `vue-tsc --noEmit`、Vitest、Vite build；登录、租户切换、看板局部失败和 AI SSE 恢复补端到端测试。

本清单以当前仓库代码为准。接口文档与实际 Controller 在租户、工作空间、成员等路径上存在差异，开发对应页面前必须先冻结一版可执行契约。

## 8. 第十三阶段发布门禁（2026-10-13）

- 统一状态、危险操作确认、路由请求取消、移动端只读看板和报告分页已完成。
- Vitest：15 个测试文件、66 个测试通过。
- Playwright：登录、数据源、数据集建模、看板和 AI 五条关键流程通过。
- Vite 生产构建通过；高级分析页 ECharts 按需化后由约 507 kB 降至约 5.6 kB。
- 权限、安全、性能和发布边界见 `docs/frontend-release-acceptance.md`。
