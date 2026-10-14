# AI BI 智能数据分析平台

P2 高级分析（异常检测、预测回测、增强语义检索和查询优化）的实现与验收说明见
[`docs/p2-advanced-analytics-acceptance.md`](docs/p2-advanced-analytics-acceptance.md)。该能力默认关闭，
仅在试点环境通过 `ANALYTICS_ENABLED=true` 开启；客户反馈与下一版本决策记录见
[`docs/p2-pilot-feedback-next-release.md`](docs/p2-pilot-feedback-next-release.md)。

数据库底座、Java API 公共能力和前端 API 请求基线已初始化。

- [数据库配置、启动与验证说明](infrastructure/README.md)
- [Java API 分层、响应与异常规范](backend/README.md)
- [前端 API 请求基线](frontend/README.md)
- [数据库运行账号最小权限](infrastructure/security/README.md)
- [MySQL 最终初始化 SQL](infrastructure/sql/mysql.sql)
- [ClickHouse 最终初始化 SQL](infrastructure/sql/clickhouse.sql)
- [MySQL 字段与关系字典](docs/mysql_data_dictionary.md)
- [文件导入、数据同步与质量实现](docs/ingestion-sync-quality.md)
- [数据集与业务指标验收](docs/acceptance-semantic-datasets-metrics.md)
- [前端图表设计器验收](docs/frontend-chart-designer.md)
- [前端可视化数据看板验收](docs/frontend-dashboard-management.md)
- [前端 AI 智能数据分析工作台](docs/frontend-ai-analysis.md)
- [前端商业化页面 P1](docs/frontend-commercial-p1.md)
- [前端自动化测试与发布验收](docs/frontend-release-acceptance.md)
- [BI 图表与看板验收](docs/acceptance-bi-visualization-dashboard.md)
- [AI 智能分析引擎验收](docs/acceptance-ai-intelligent-analysis.md)
- [LangGraph AI 服务启动说明](ai-service/README.md)
- [P0 系统集成与交付验收](docs/p0-system-integration-acceptance.md)
- [P0 数据对账报告](docs/p0-data-reconciliation.md)
- [P0 已知限制](docs/p0-known-limitations.md)
- [P0 封闭试点发布清单](docs/p0-pilot-checklist.md)
- [P1 商业化试运营验收记录](docs/p1-commercial-pilot-acceptance.md)

```powershell
./infrastructure/scripts/Initialize-Database.ps1 -Target MySQL -Test
```

本地连接配置放在 infrastructure/.env；不要提交数据库密码。

应用层 P0 门禁可执行：

```powershell
./scripts/p0/Invoke-P0Gate.ps1
```

P1 商业化门禁与备份恢复演练：

```powershell
./scripts/p1/Invoke-CommercialGate.ps1 -IncludeFrontend -IncludeInfrastructure
./scripts/p1/Invoke-BackupRestoreDrill.ps1
```
