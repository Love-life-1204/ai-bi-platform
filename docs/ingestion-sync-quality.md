# 文件导入、数据同步与质量实现

## 实现范围

| 编号 | 状态 | 实现摘要 |
|---|---|---|
| 65 | 完成 | CSV 扩展名、MIME、100 MB 上限、UTF-8/NUL 和表格边界校验 |
| 66 | 完成 | XLSX 包签名、ZIP 炸弹、宏、嵌入对象、外部链接和公式防护 |
| 67 | 完成 | 预览行、字段类型推断、日期格式推断与显式映射确认 |
| 68 | 完成 | 导入任务幂等创建、列表、状态和取消，独立有界 Worker 池异步执行 |
| 69 | 完成 | 异常行聚合、严格/宽松模式、防公式注入的 CSV 错误报告 |
| 70 | 完成 | 同步任务新增、编辑、启用、暂停和乐观锁 |
| 71 | 完成 | 全量 Worker 按稳定主键 keyset 分页抽取，不使用无界内存队列 |
| 72 | 完成 | 白名单字段名和 STRING/LONG/DECIMAL/BOOLEAN/DATE/DATETIME 类型映射，映射后进入 ClickHouse RAW 事件 |
| 73 | 完成 | 全量每次使用新 `generation_id`，校验前不改变可见代 |
| 74 | 完成 | 增量使用 `(updated_at, primary_key)` 组合水位和稳定排序 |
| 75 | 完成 | 持久化幂等键覆盖租户/数据集/源/代/发布序号/业务键/源版本 |
| 76 | 完成 | 原子租约抢占、续约和单调 `fencing_token` |
| 77 | 完成 | REQUIRED、UNIQUE、TYPE、RANGE 质量规则 |
| 78 | 完成 | 源/目标行数、金额、业务键数对账 |
| 79 | 完成 | 质量门禁后的发布指针 CAS，与批次状态、水位同事务提交 |
| 80 | 完成 | 有界重试、死信持久化、人工补偿及操作审计 |
| 81 | 完成 | 同步任务列表、批次进度、质量/对账和失败/死信详情页面 |
| 82 | 完成 | 自动化测试覆盖写入中断、重放、迟到更新、删除与金额不重复累计 |

## 一致性边界

ClickHouse 行事件先按幂等键写入 RAW，MySQL 保存收件、行证据、质量结果、死信和发布指针。跨库不使用分布式事务：ClickHouse 写入失败时保留 MySQL `PENDING/FAILED` 证据以便重试；只有对账和质量全部通过才 CAS 发布。查询侧必须限定 `active_generation` 和 `committed_publish_seq`，再按业务键取最新版本，删除事件不参与最终金额。

## 验证

```powershell
cd backend
mvn "-Dmaven.repo.local=.m2" clean test

cd ../frontend
npm run ci
npm run build

cd ..
$env:PYTHONPATH='infrastructure/.local/python'
python infrastructure/tests/test_initialization.py
```

后端正确性场景集中在 `SyncCorrectnessTest`，文件安全与 API 场景集中在 `FileImportApiIntegrationTest`。真实 ClickHouse 回归需先启动本地 ClickHouse 实例。
