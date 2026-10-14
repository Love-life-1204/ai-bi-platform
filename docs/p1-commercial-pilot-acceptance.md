# P1 商业化能力验收记录

更新日期：2026-10-13（Asia/Shanghai）

## 结论

本轮已完成商业化服务的可运行纵切、MySQL 持久化与跨实例事务锁、数据库迁移和自动化门禁。
代码已具备试运营能力，但尚不能宣布目标环境“付费试运营 GO”：仍须在预发布环境绑定真实支付沙箱、
OIDC IdP 与 SMTP 凭据，执行端到端回调/登录/投递测试，并将 V002—V007 应用到目标环境后签字。
当前交付以服务端 API 和运维脚本为主，套餐、账单、报告、告警与平台运营的正式管理端页面仍待实现。

## 156—167 实施状态

| 步骤 | 状态 | 实施与证据 |
|---|---|---|
| 156 套餐及配额版本 | 已实现 | 套餐版本、价格快照、币种、账期和配额集；`GET /api/plans`。 |
| 157 订阅、订单与回调 | 已实现 | 租户订阅、服务端定价订单、变更套餐和支付回调状态机。 |
| 158 验签、幂等、退款 | 已实现 | HMAC-SHA256 + 时间窗 + 原始 body 摘要；事件重放不重复生效；累计退款不得超过订单。 |
| 159 配额预留/确认/释放 | 已实现 | 原子状态机、业务事件幂等和不负数保护；AI 任务和导出任务已接入。 |
| 160 模型网关与外发策略 | 已实现 | 外部模型默认关闭；`DISABLED/AGGREGATE_ONLY/REDACTED/APPROVED_DETAIL`；敏感数据强制拒绝。 |
| 161 Token 量与对账 | 已实现 | 供应商 request id 去重、输入/输出 Token 计价、估算费用与供应商金额差异。 |
| 162 MFA/OIDC | 已实现 | TOTP 因子 AES-GCM 加密持久化并防时间步重放；OIDC 完成 state/nonce/PKCE、授权码交换、issuer/JWKS/audience/azp/email 校验及本地会话签发。 |
| 163 报告与异步导出 | 已实现 | 报告保存、独立有界线程池、CSV/XLSX/PDF、取件再授权、过期、取消和 CSV/XLSX 公式注入防护。 |
| 164 定时报告与收件人鉴权 | 已实现 | 有界轮询调度；每个收件人按投递时成员状态/权限版本重新判定。 |
| 165 阈值告警与抑制 | 已实现 | GT/GTE/LT/LTE/EQ、评估窗去重、抑制窗、证据保留、通知和确认；可配置 SMTP 通道。 |
| 166 运营、审计、支持授权 | 已实现 | 运营仪表、追加审计、工单号+最小范围+最长 2 小时的支持授权；平台/内部 token 与租户 token 分离。 |
| 167 安全、备份恢复、试运营验收 | 部分完成 | 安全核心项与 V007 全量恢复演练 PASS（93 表，RTO 5s）；目标环境真实供应商联调和发布签字待完成。 |

## 自动化证据

- Java：`mvn test` 通过，89 个测试（其中 1 个环境用例跳过）；商业化新增 6 个单测。
- Checkstyle：`mvn -q checkstyle:check` 通过。
- MySQL：全新隔离库按 `mysql.sql -> V002 -> ... -> V007` 完整迁移，17 个真库回归通过，验收库已删除。
- 备份恢复：[`restore-evidence.json`](../artifacts/p1/backup-restore/restore-evidence.json)，原始 dump 在校验后删除，隔离恢复库已清理。

## 安全评审

| 检查 | 结果 |
|---|---|
| 支付回调伪造、过期、重放、金额/币种不符 | PASS |
| 配额并发超售、重复结算、余额负数 | PASS |
| 租户跨界、导出人与收件人权限撤销 | PASS |
| 外部模型默认关闭、敏感数据拒绝 | PASS |
| CSV/XLSX 公式注入、导出文件过期 | PASS |
| MFA 动态码时间窗与重放 | PASS |
| OIDC issuer/JWKS/audience/nonce 校验与登录会话签发 | PASS（代码）；真实 IdP 联调待执行 |
| 商业化状态的多实例持久化和事务一致性 | PASS（MySQL JSON 状态 + 行锁事务） |
| MFA 因子持久化及静态加密 | PASS（AES-256-GCM + 用户 AAD） |
| 邮件/企业通知通道 | PASS（SMTP 适配器）；真实 SMTP 联调待执行 |

## 上线前外部动作

1. 配置 `PAYMENT_WEBHOOK_SECRET`，使用支付沙箱完成成功、失败、重复、篡改与退款回调。
2. 配置 `APP_COMMERCIAL_OIDC_SECRETS_0_*`，使用真实 IdP 完成登录、nonce 错误、签名轮换和禁用用户测试。
3. 配置 `SPRING_MAIL_*` 与 `COMMERCIAL_EMAIL_*`，验证投递、失败监控和供应商侧退订/退信治理。
4. 在目标环境备份后应用 V002—V007，重跑商业化门禁与恢复演练并完成业务签字。
5. 完成商业化管理端页面及产品验收，避免试运营依赖直接调用管理 API。

## 运行命令

```powershell
./scripts/p1/Invoke-CommercialGate.ps1 -IncludeFrontend -IncludeInfrastructure
./scripts/p1/Invoke-BackupRestoreDrill.ps1
```

备份演练脚本只在 `aibi_restore_*` 隔离库中恢复，默认演练完自动清理；`-KeepRestoreDatabase` 仅用于人工取证。
