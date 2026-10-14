# 数据集与业务指标验收说明（83–97）

## 已实现范围

| 编号 | 能力 | 实现与门禁 |
|---|---|---|
| 83 | 数据集创建与工作空间校验 | 租户令牌、活动工作空间、活动成员三项同时校验；稳定编码按租户和工作空间唯一。 |
| 84 | 字段选择、类型与业务名称映射 | 字段保留稳定键、源字段 ID、物理名、业务名和受控业务类型。 |
| 85 | 维度、度量与敏感等级 | 字段角色仅允许 `DIMENSION/MEASURE`；敏感等级仅允许 `PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED`。 |
| 86 | 行列级权限预览 | 复用现有 RLS/CLS 计算；同行策略组 OR、异组 AND，列执行拒绝或掩码后才返回样例。 |
| 87 | 批准的数据集关联 | 所有关联发布前必须显式 `approved=true`，且目标必须是同租户、同工作空间的已发布版本。 |
| 88 | 基数与重复维度键 | 禁止多对多；一对一/多对一要求右键唯一，或使用 `FIRST/LAST/MAX_UPDATED_AT` 去重规则。 |
| 89 | 数据集草稿、校验与发布 | 实现 `DRAFT → VALIDATED → PUBLISHED`，发布定义带 SHA-256 哈希，发布后修改生成后继草稿。 |
| 90 | 业务术语和同义词 | 支持创建、更新、查询和审核发布，同义词去重保存。 |
| 91 | 指标创建 | 稳定指标包含名称、编码、工作空间和单位，版本固定引用数据集版本。 |
| 92 | 公式 AST 与白名单 | 仅允许字段、字面量、版本指标引用、受控聚合/数学/条件/时间/比较算子，不接受 SQL。 |
| 93 | 时间、时区、金额精度及币种 | IANA 时区、`[start,end)`、精度 0–8；跨币种必须指定目标币种及批准汇率来源，金额使用 `BigDecimal`。 |
| 94 | 指标依赖与环检测 | 依赖固定到已发布指标版本，发布前进行 DAG 循环检测。 |
| 95 | 指标草稿、发布和历史版本 | 实现不可变发布版本、当前版本指针和历史版本 API。 |
| 96 | 管理前端 | 增加“数据集与指标”入口、列表、字段编辑、公式配置、校验、发布和权限预览页面。 |
| 97 | 固定订单样例 | 自动化测试覆盖支付 100、支付 200 退款 50、取消 300，净销售额为 `250.00 CNY`；同时覆盖跨币种拒绝/换算和零分母 `UNDEFINED`。 |

## 主要 API

- 数据集：`/api/datasets`、`/{id}/validate`、`/{id}/publish`、`/{id}/versions`、`/{id}/preview`。
- 指标：`/api/metrics`、`/{id}/validate`、`/{id}/publish`、`/{id}/versions`。
- 业务术语：`/api/terms`、`/{id}`、`/{id}/publish`。
- 所有端点同时提供 `/api/v1` 前缀版本，并要求 `Authorization` 与 `X-Tenant-Id`。

## 验证命令

```powershell
cd backend
mvn test

cd ../frontend
npm run check
npm test
npm run build
```

数据库启用模式额外执行 `python infrastructure/scripts/db.py mysql migrate`，以应用
`V003__semantic_metric_unit.sql`。生产发布前仍需在目标数据规模上对关联唯一性检查和权限预览限流进行压测。

## 阶段验收：净销售额指标

自动化验收 `NetSalesMetricStageAcceptanceTest` 已走通“创建草稿 → 公式校验 → 发布不可变版本”，
并确认指标当前版本指针指向已发布版本。查询编译验收同时确认条件公式会生成参数化
ClickHouse `if(...)`，不会把状态值拼入 SQL。

### 业务口径

- 指标：`net_sales` / 净销售额，单位 `CNY`，保留 2 位小数。
- 适用数据集：`sales_orders` / 销售订单，验收数据集版本为不可变版本 `v4`（fixture ID `104`）。
- 公式：`SUM(IF(order_status = 'PAID', paid_minor_units - refunded_minor_units, 0)) / 100`。
- 退款：`refunded_minor_units` 只保存已经确认成功的退款；申请中、失败或撤销退款不写入该字段，
  因此不扣减。部分退款按确认金额扣减，全额退款会把该订单净额降为 0。
- 订单状态：只统计 `PAID`；`CANCELLED`、`PENDING_PAYMENT` 和 `FAILED` 均不计入。
- 时间：使用 `business_time`，时区 `Asia/Shanghai`，筛选区间固定为 `[start, end)`。
- 币种：本验收版本使用 `SINGLE` 模式，按 `currency` 分区或筛选；未经批准汇率不得跨币种直接求和。

### 字段绑定

| 稳定字段键 | 物理字段 | 业务含义 | 用途 |
|---|---|---|---|
| `order_status` | `order_status` | 订单状态 | 只纳入 `PAID` |
| `paid_minor_units` | `paid_minor_units` | 实付金额（分） | 被减数 |
| `refunded_minor_units` | `refunded_minor_units` | 已确认退款金额（分） | 扣减项 |
| `business_time` | `business_time` | 支付业务时间 | 时间范围 |
| `currency` | `currency` | ISO 4217 币种 | 单币种门禁 |
| `region_id` | `region_id` | 区域 | 允许分析维度 |

固定样例为：支付 100 元、支付 200 元并确认退款 50 元、取消 300 元，结果为
`100 + (200 - 50) + 0 = 250.00 CNY`。
