# 前端 API 基线

P2 高级分析导航默认隐藏。试点构建同时设置 `VITE_ANALYTICS_ENABLED=true`，并确保后端
`ANALYTICS_ENABLED=true`；一般商业环境保持两个开关关闭。

`src/api/client.ts` 集中处理：

- 统一 `ApiResponse<T>` 解包与 `ApiClientError`。
- Bearer Token、租户/成员、`Idempotency-Key` 和 `X-Trace-Id` 请求头。
- 默认 15 秒超时、主动取消、网络异常和错误提示回调。
- 并发请求 Loading 计数，避免任一请求结束后过早关闭全局 Loading。

UI 层在创建 `ApiClient` 时注入 Token/租户获取器、消息提示和 Loading 回调，不在业务页重复编写拦截逻辑。

```powershell
cd frontend
conda run --no-capture-output -n AI_BI_Platform npm ci --cache .npm-cache
conda run --no-capture-output -n AI_BI_Platform npm run ci
```

## 数据源页面

`src/datasources` 提供数据源列表、创建/编辑表单、连接测试状态和 API 封装。页面区分连接成功、认证失败、网络超时、账号权限不足与安全策略拦截，不会把凭据写入浏览器存储。

## 数据同步页面

`src/sync` 提供同步任务列表、启停、手动触发、增量水位、批次进度、质量/对账结果、失败详情和死信人工补偿。页面不自行推进水位或发布指针，只呈现后端状态机。

```powershell
cd frontend
conda run --no-capture-output -n AI_BI_Platform npm run dev
# 生产构建
conda run --no-capture-output -n AI_BI_Platform npm run build
```

前端与后端统一在 `AI_BI_Platform` Conda 环境中运行。Windows 下必须使用
`--no-capture-output`，避免 Conda 捕获 Vite/Vitest Unicode 输出时受系统 GBK 编码影响。

## 公共 UI

应用已迁移到 Vue 3 + Router + Pinia + Element Plus。所有业务路由共用左侧导航、顶部工具栏、
面包屑和页面标题；视觉令牌及公共组件规范见 `docs/frontend-ui-design-system.md`，交互示例位于
`/design-system`。

API Client 默认以同源 `/api` 为 `baseUrl`，业务 API 模块只传资源路径，不在页面组件中拼接接口地址。本地开发把 `/api` 代理到 `http://127.0.0.1:8080`；也可通过 `VITE_API_BASE_URL` 指向包含 `/api` 根路径的后端地址。

## 登录与会话

`/login` 依次调用全局登录、成员关系、企业切换和工作空间接口。普通业务请求使用租户上下文令牌；全局令牌仅用于刷新、退出、成员关系和企业切换。受保护路由由全局守卫拦截，401 会合并为一次刷新并重放原请求，刷新失败回到登录页，403 进入统一无权页面。实现与安全边界见 `docs/frontend-authentication.md`。
