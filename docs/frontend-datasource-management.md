# 前端数据源管理实现说明

更新日期：2026-10-12  
覆盖任务：033—040

## 1. 已实现能力

- 数据源列表展示名称、类型、连接地址、连接状态、负责人、最近测试时间和启停状态。
- 新增入口支持 MySQL、PostgreSQL 和 CSV/XLSX 文件数据源。
- 数据库表单支持地址、端口、库名、TLS、用户名和密码，并同时支持保存前测试与已保存连接测试。
- 连接测试统一区分成功、认证失败、网络错误、超时、只读权限不足、服务不可用和无权访问。
- 元数据浏览器支持采集并展示 Schema、表、字段、字段类型、可空性和主键信息；已选源表按企业与工作空间隔离保存，可带入同步任务页面。
- 文件上传在浏览器端校验扩展名和 100 MB 上限，上传后展示推断字段和样例数据。
- 编辑时凭据输入始终为空；服务端数据源响应不包含凭据字段，前端不会回显或持久化已保存的用户名、密码。
- 停用与删除前查询同步任务依赖并展示影响。只有已停用且无依赖的数据源可以删除。
- 企业或工作空间切换会取消旧请求，并清除数据源选择、图表和 AI 上下文缓存。

## 2. 页面与接口

主页面为 `frontend/src/views/legacy/DatasourcePage.vue`，路由为 `/datasources`。

| 页面动作 | 接口 |
|---|---|
| 列表 | `GET /api/datasources?workspaceId=` |
| 新增 / 编辑 | `POST /api/datasources`、`PUT /api/datasources/{id}` |
| 草稿 / 已保存连接测试 | `POST /api/datasources/connection-tests`、`POST /api/datasources/{id}/connection-test` |
| 元数据采集与版本 | `POST /api/datasources/{id}/metadata/collect`、`GET /api/datasources/{id}/metadata/snapshots` |
| 依赖检查 | `GET /api/datasources/{id}/dependencies` |
| 停用 / 删除 | `POST /api/datasources/{id}/deactivate`、`DELETE /api/datasources/{id}` |
| 文件上传 / 预览 | `POST /api/files/uploads`、`GET /api/files/{id}/preview` |

请求统一由 `ApiClient` 注入 Bearer Token、企业与成员上下文；401 触发一次令牌刷新和请求重放，403 进入统一无权限页。业务权限仍由 Spring Boot 校验，前端按钮和路由隐藏不作为授权边界。

## 3. 安全边界

1. 数据源列表、详情和连接测试结果只包含连接活动信息，不返回 `credential`。
2. 编辑页面不会把原凭据写入表单；用户留空代表保留原凭据，填写新值才触发更新。
3. 保存后的连接测试由服务端读取加密凭据，浏览器不缓存密码。
4. 文件类型、大小和租户隔离由服务端再次校验，浏览器校验仅用于提前反馈。
5. 删除操作使用乐观锁，并由服务端再次验证停用状态和同步任务依赖。

## 4. 验证结果

- `npm run check`：通过。
- `npm run ci`：通过，6 个测试文件、35 个测试。
- Spring Boot 全量编译：通过。
- 数据源定向 Checkstyle：0 个违规。
- `DatasourceApiIntegrationTest`、`DatasourceStageAcceptanceTest`、`MetadataVersionDiffTest`：共 5 个测试通过。

