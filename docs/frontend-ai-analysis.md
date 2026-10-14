# 前端 AI 智能数据分析工作台（第十一阶段）

完成日期：2026-10-13

## 实现范围

任务 085—096 已完成。`/ai` 现在是受认证保护的原生 Vue 工作台，包含：

- 当前工作空间内的持久化会话列表、搜索、新建、历史消息和最新任务恢复。
- 工作空间、全部授权语义资产或指定已发布数据集的分析范围选择。
- 中文问题输入、防重复提交，以及幂等键在不确定失败时的重试复用。
- AI 任务 `task_id` 保存和 accepted、progress、clarification、result、error、done 全状态轨迹。
- SSE 断线重连、`Last-Event-ID` 恢复、页面重开后的任务恢复，以及切换企业/工作空间时主动中止旧事件流。
- 指标、时间和业务口径三类澄清 UI；恢复任务前仍由服务端重新鉴权。
- 事实、假设、建议和限制分区；无数据时不虚构图表或成功结果。
- 仅允许 KPI、柱状图、折线图、饼图、明细表的安全 `ChartSpec` 映射，不执行模型返回的脚本、HTML 或任意 ECharts 配置。
- 查询证据抽屉，展示查询 ID、追踪 ID、数据集版本、指标版本、查询时间和发布版本。
- 停止、失败重试、历史消息、正负反馈及反馈说明。
- 无权限、权限撤销、无数据、AI 超时、模型不可用和任务取消的明确状态文案。

## 安全边界

- 前端只选择服务端返回的已发布数据集，不接收或提交任意 SQL。
- 会话、任务恢复、SSE、证据和反馈均依赖 Spring Boot 当前成员上下文重新鉴权；前端隐藏能力不作为授权依据。
- AI 数值事实只来自受控 `QueryPlan` 的真实查询结果；建议和假设与事实分开展示。
- AI 服务证据新增 `trace_id` 和查询 `time_range`，方便从回答追踪到查询和发布版本。
- ECharts 渲染层只消费前端重建的白名单 `ChartSpec` 与标准化结果行。

## 验证结果

| 检查 | 结果 |
|---|---|
| `npm run check` | 通过 |
| `npm run ci` | 12 个测试文件、60 项测试全部通过 |
| `npm run build` | 通过；AI 页面独立异步块约 24.65 kB（gzip 约 9.45 kB） |
| AI 服务 pytest | 11 项全部通过（项目 `.venv`） |
| Spring Boot 相关测试 | 4 项全部通过：AI 工具幂等、禁用成员、权限版本重新鉴权、P0 业务链路 |

当前 `AI_BI_Platform` Conda 环境能运行 pytest，但尚未安装 FastAPI、LangGraph、httpx、Pydantic 等 AI 服务依赖；项目 `.venv` 的依赖完整并通过测试。若生产/开发统一使用该 Conda 环境，应先在该环境执行 `pip install -e ".[test]"`。

## 主要文件

- `frontend/src/views/AiAnalysisPage.vue`
- `frontend/src/ai/api.ts`
- `frontend/src/ai/types.ts`
- `frontend/src/ai/sse.ts`
- `frontend/src/ai/presentation.ts`
- `frontend/src/ai/presentation.test.ts`
- `ai-service/app/services/task_manager.py`

