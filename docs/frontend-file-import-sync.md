# 文件导入与数据同步前端实现说明

更新日期：2026-10-13  
覆盖任务：041—049

## 1. 页面能力

`/sync` 已由旧的原生 DOM 原型升级为原生 Vue 3 工作台，分为“文件导入”和“数据库同步”两个区域。

文件导入：

- 支持 CSV、XLSX 文件选择、100 MB 前端限制、上传中和安全校验反馈。
- 上传后调用后端解析接口，展示工作表、抽样行、推断字段类型和日期格式。
- 支持修改目标字段、目标类型、日期格式、必填项和唯一键。
- 唯一键对应后端映射列表的首个稳定业务键；严格模式对应 `strictMode=true`，容错模式会跳过异常行并生成错误报告。
- 选择目标数据集后提交异步任务，界面只显示后端返回的 `PENDING`、`RUNNING` 或终态。
- 导入记录展示有效行、错误行、总行数、错误报告文件编号和失败原因，并支持取消未结束任务。

数据库同步：

- 同步任务列表展示数据源、源表、目标数据集、全量/增量模式、最近运行时间和任务状态。
- 创建/编辑表单从已采集元数据生成字段映射，支持主键、更新时间字段、金额字段、批次大小、Cron 和时区。
- 增量模式必须配置更新时间字段；任务启用与立即运行互相独立。
- 同步记录展示运行尝试、批次行数、异常行、去重行、质量状态、行数/金额/业务键对账和失败原因。
- 排队中或运行中的任务支持确认后取消；失败任务支持填写原因并创建新的补偿运行。
- 页面每 4 秒只轮询存在活动任务的状态，离开页面时停止轮询。

## 2. 服务端状态是唯一事实来源

前端不会因为 `POST /imports` 或 `POST /sync-jobs/{id}/run` 返回 202 就显示成功：

1. 请求提交后显示接口返回的 `PENDING` 状态。
2. 通过 `GET /imports`、`GET /imports/{id}`、`GET /sync-runs` 和 `GET /sync-runs/{id}` 获取真实状态。
3. 只有服务端返回 `SUCCESS` 才展示成功；`PARTIAL_SUCCESS`、`FAILED`、`CANCELLED` 和 `TIMEOUT` 分别展示独立状态。
4. 任务进度来自服务端返回的有效行、异常行和总行数，不使用前端虚构完成进度。

## 3. 接口映射

| 能力 | 接口 |
|---|---|
| 文件上传与预览 | `POST /api/files/uploads`、`GET /api/files/{id}/preview` |
| 创建、查询和取消导入 | `POST /api/imports`、`GET /api/imports`、`GET /api/imports/{id}`、`POST /api/imports/{id}/cancel` |
| 创建、编辑、启停同步任务 | `POST /api/sync-jobs`、`PATCH /api/sync-jobs/{id}`、`POST /api/sync-jobs/{id}/enable|pause` |
| 运行同步任务 | `POST /api/sync-jobs/{id}/run` |
| 查询与取消运行 | `GET /api/sync-runs`、`GET /api/sync-runs/{id}`、`POST /api/sync-runs/{id}/cancel` |
| 失败补偿 | `POST /api/sync-runs/{id}/compensate` |

所有创建和运行请求沿用统一 `ApiClient` 的身份、租户、成员和幂等请求头。导入任务的真实发起成员由 Spring Boot 从已验证的租户令牌提取，不信任请求体中的成员 ID。页面还会按当前工作空间的数据源和数据集过滤任务，企业或工作空间切换后由 Layout 重建页面并取消旧请求。

## 4. 验证结果

- `npm run ci`：通过，7 个测试文件、38 个测试。
- `npm run build`：通过。
- 状态单元测试覆盖排队/运行、部分成功、失败、死信和后端行数进度计算。
- `FileImportApiIntegrationTest`：3 个测试通过，覆盖 CSV/XLSX、安全拒绝、预览、异步导入和错误报告。
- `SyncCorrectnessTest`：3 个测试通过，覆盖租约 fencing、增量回放/删除、去重、失败和死信。

