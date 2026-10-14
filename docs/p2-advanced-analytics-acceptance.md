# P2 高级功能与持续优化验收

日期：2026-10-13（Asia/Shanghai）

## 范围与发布原则

P2 使用独立入口和 `ANALYTICS_ENABLED` 开关，默认关闭。算法不访问数据库，权限服务先确认租户、工作空间、`metric:read` 和已发布指标版本，再把已授权序列交给计算层。这样不会改变 P0/P1 的同步、统一查询、计费或 AI 问答主链路。

## 168—173 完成映射

| 步骤 | 实现 | 验收点 |
|---|---|---|
| 168 | `POST /api/v1/analytics/anomalies/detect`；业务上下限与滚动 Z 分数并集 | 统计基线只读取当前点之前的窗口；响应包含原因、基线、标准差和上下带 |
| 169 | `POST /api/v1/analytics/forecast/windows` | 滚动训练/验证窗口索引和时间边界明确，`trainingEnd < validationStart` |
| 170 | `POST /api/v1/analytics/forecast` | 返回 MAE、RMSE、MAPE、区间覆盖率，以及每个未来点的预测值、置信水平、上下界 |
| 171 | AI 服务 `semantic_ranker` 与独立 fixture 评测 | 覆盖别名、模糊召回、多指标比较/相关、趋势、异常和预测；离线门禁不调用在线服务 |
| 172 | 查询缓存容量上限、命中/未命中/淘汰统计；ClickHouse 发布序列、时间、状态跳数索引 | 缓存不再无界增长；索引优先覆盖编译器固定注入条件 |
| 173 | `p2-pilot-feedback-next-release.md` | 反馈证据、评分、版本准入和退出标准可审计，不虚构客户反馈 |

## API 示例

开启试点环境：

```text
ANALYTICS_ENABLED=true
VITE_ANALYTICS_ENABLED=true
```

预测请求中的 `points` 必须是同一已发布业务指标的时间序列。建议前端先通过统一查询取得数据，再原样提交 `metricVersionId`、时间戳和值。服务会排序时间点，拒绝重复时间戳、非有限值、训练数据不足和无效季节长度。

预测区间是统计基线区间，不等于业务承诺。当前模型为线性趋势或季节朴素法；它的目的，是给试点提供可解释基线和回测证据，后续模型只有在独立回测稳定胜出时才替换。

## 验证命令

```powershell
cd backend
mvn "-Dtest=TimeSeriesAnalyticsManagerTest,QueryResultCacheTest" test

cd ..\ai-service
.\.venv\Scripts\python.exe -m pytest -p no:cacheprovider `
  --basetemp .tmp\pytest-p2 `
  tests/unit/test_semantic_evaluation.py tests/unit/test_graph_recovery.py -q

.\.venv\Scripts\python.exe scripts/evaluate_semantic.py `
  tests/fixtures/p2_semantic_eval.json
```

## ClickHouse 上线步骤

1. 先在影子环境执行 `infrastructure/sql/clickhouse.sql` 的 `ADD INDEX IF NOT EXISTS`。
2. 用代表性租户运行 `EXPLAIN indexes = 1`，确认发布序列、业务时间或状态索引确实裁剪 granule。
3. 新数据自动写入索引；历史 part 只在低峰按分区执行 `MATERIALIZE INDEX`，每批观察查询 P95、合并队列和磁盘 I/O。
4. 若 P95 或后台合并恶化，停止物化；索引可用 `DROP INDEX` 回滚，不影响表数据和查询正确性。

## 当前边界

- 算法输入上限为 10,000 点，防止单请求抢占线程和内存。
- API 当前同步执行，适合试点中的中小序列；大规模训练应走独立有界任务队列。
- 预测基线不处理节假日、促销、缺失值插补和外生变量；这些必须通过下一轮试点评测证明收益后引入。
- 缓存统计当前为进程内观测；生产监控接入 Micrometer/Prometheus 属于上线前运维项。
