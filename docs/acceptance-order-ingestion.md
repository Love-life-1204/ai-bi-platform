# 企业订单数据同步阶段验收

## 验收口径

1. 企业订单数据可从只读源库分页抽取，经受控字段映射写入 ClickHouse。
2. 同一源事件重放或同一批次重试时，指标查询按业务键取最新版本，金额不重复累计。
3. 只允许 MySQL 发布指针指向的 `generation_id` 且 `publish_seq <= committed_publish_seq` 的数据被查询；质量失败或写入中断批次不推进指针。

## 自动化证据

- `SyncCorrectnessTest`：验证过期租约被 fencing、重复事件只接收一次、未发布失败序号不可见、迟到更新和删除后金额不重复。
- `test_clickhouse.py`：在随机临时 ClickHouse 库中验证重放、未发布失败序号、删除、generation 切换和租户隔离。
- `EnterpriseOrderClickHouseAcceptanceTest`：创建隔离 MySQL 企业订单源表，通过正式源表分页读取器、映射器和 RAW Writer 写入 `ai_bi_raw.ingest_event`，使用临时最小权限账号并在结束后清理。

## 本次验收结果

| 验收项 | 结果 | 证据/阻塞 |
|---|---|---|
| 源端订单进入 ClickHouse | 通过 | 真实 MySQL 源表抽取和映射后，应用 Writer 成功写入 RAW，查询确认订单号和金额 125.50 |
| 重复同步不重复统计 | 通过 | Java 状态机回归与 ClickHouse 真库 `argMax` 查询均通过，重复事件不重复累计 |
| 失败批次不可见 | 通过 | 已发布 seq=1 保持可见，中断/Replay seq=2 不在 `committed_publish_seq=1` 的可见集合中 |

本次三项必须项均已取得真库或状态机证据，整体结论为 **通过**。

## 执行

```powershell
cd backend
mvn "-Dmaven.repo.local=.m2" "-Dtest=SyncCorrectnessTest" test

# 先将 infrastructure/.env 中的测试连接配置注入环境，再执行：
$env:RUN_EXTERNAL_INGESTION_ACCEPTANCE='true'
mvn "-Dmaven.repo.local=.m2" "-Dtest=EnterpriseOrderClickHouseAcceptanceTest" test

cd ..
$env:PYTHONPATH='infrastructure/.local/python'
python infrastructure/tests/test_clickhouse.py
```

Java 回归不依赖外部数据库，用于快速阻断状态机和发布算法回归。Python 用例验证 ClickHouse 真库的查询去重与发布可见性；外部验收用例则使用真实 MySQL 和 ClickHouse 配置验证源端到 RAW 表的完整写入链路。ClickHouse 不可用或外部验收用例未通过时，必须判定阶段验收未完成，不得以 Java 模拟结果代替。
