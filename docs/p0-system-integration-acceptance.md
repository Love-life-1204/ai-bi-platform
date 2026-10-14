# P0 系统集成与交付验收

记录日期：2026-10-12（Asia/Shanghai）

## 结论

应用层 P0 门禁已建立，核心黄金链路、隔离、幂等、金额口径、时区边界、安全查询和资源公平性均有自动化测试。当前仓库不能据此直接宣布封闭试点 GO：本机缺少 Docker 和 k6，MySQL 测试库也未应用最新 V002—V004 迁移，因此真实混合并发和四类组件故障恢复尚无本轮通过证据。

## 144—155 验收矩阵

| 步骤 | 状态 | 自动化证据 / 结论 |
|---:|---|---|
| 144 | PASS | `P0BusinessJourneyAcceptanceTest` 串联租户、数据源元数据、同步、数据集、指标、图表、看板与 AI 授权目录。 |
| 145 | PASS | 同一黄金旅程从企业开通执行到首个看板发布；CI 后端任务强制执行。 |
| 146 | PASS | `MultiTenantPermissionStageAcceptanceTest`、`RlsClsIntegrationTest`、`PermissionVersionReauthorizationIntegrationTest`。 |
| 147 | PASS | `FileImportApiIntegrationTest` 覆盖 CSV 部分成功、二进制 CSV、XLSX 公式拒绝与重复请求；`SyncCorrectnessTest` 覆盖重放、乱序更新、删除与质量对账。 |
| 148 | PASS | `MetricCalculationManagerTest` 覆盖金额、退款、跨币种、零分母；`QueryPlanCompilerTest` 覆盖 Asia/Shanghai 闰日到 UTC 的 `[start,end)` 边界。 |
| 149 | PASS（P0 范围） | 强类型 QueryPlan 拒绝原始 SQL/未知授权字段；AI 提示词不能写可信状态；统一拦截器校验租户令牌与租户头，文件跨租户下载返回 403；数据源响应和错误不泄露凭据。 |
| 150 | READY | `tests/performance/mixed-workload.js` 已包含查询、同步、AI、下载四类负载；待试点环境执行。P1 报表导出尚未实现，当前以受控文件下载作为 I/O 负载。 |
| 151 | PARTIAL | 自动化验证单租户并发槽隔离；P95、错误率及 20 租户报告须由 k6 实跑生成。 |
| 152 | READY | `Invoke-FaultDrill.ps1` 可对 MySQL、ClickHouse、Redis、AI Worker 逐项停启并记录恢复时间；待具备完整 Compose/试点环境后执行。 |
| 153 | PASS（已运行范围） | 后端、前端现有门禁无阻断级/高优先级失败。环境类未执行项不得降级为通过。 |
| 154 | PASS | 本文、`p0-data-reconciliation.md` 与 `p0-known-limitations.md` 构成交付包。 |
| 155 | NO-GO | 需补齐 20 租户混合压测、四类故障演练、真实数据库回归和业务负责人签字。 |

## 可重复执行

应用层门禁：

```powershell
./scripts/p0/Invoke-P0Gate.ps1
```

完整门禁（需要数据库、k6 和 20 个租户夹具）：

```powershell
./scripts/p0/Invoke-P0Gate.ps1 -IncludeInfrastructure -IncludeLoad
```

脚本将机器可读/可审阅结果写入 `artifacts/p0/p0-gate-result.md`。任一步骤失败即返回非零退出码，禁止发布。

## 试点 GO 条件

1. 完整门禁为绿，查询 P95 ≤ 3 秒、AI P95 ≤ 20 秒、错误率 < 1%。
2. 至少 20 个租户参与公平性压测，噪声租户不能耗尽其他租户的并发槽。
3. MySQL、ClickHouse、Redis、Worker 故障演练均形成 JSON 证据且满足批准的 RTO/RPO。
4. 阻断级和高优先级缺陷为 0；例外项必须有负责人、到期日和书面豁免。
5. 产品、研发、测试、运维和试点企业负责人完成 `p0-pilot-checklist.md` 签字。

