# 数据库配置与初始化

数据库入口集中在本目录。`sql/mysql.sql` 和 `sql/clickhouse.sql` 是两种数据库各自唯一的最终初始化 SQL；连接配置统一从 `.env` 读取，环境变量可覆盖同名配置。

## 目录

```text
infrastructure/
├── .env.example                 # 可提交的配置模板
├── .env                         # 本机连接凭据，不提交
├── requirements.txt             # Conda 环境内安装的 PyMySQL 固定依赖
├── sql/
│   ├── mysql.sql                # 79 张表、379 个外键、20 个权限码
│   └── clickhouse.sql           # 4 个库、8 张业务表
├── docker/compose.yaml          # ClickHouse 本地启动
├── scripts/
│   ├── Initialize-Database.ps1  # Windows 统一入口
│   ├── db.py                    # 连接、基线检查、初始化与状态
│   └── provision_mysql_roles.py # 应用与 Worker 表级运行角色
└── tests/                       # 初始化保护、MySQL 约束、ClickHouse 口径回归
```

脚本使用当前激活的 Python 环境，不再优先加载项目内依赖缓存。已删除分版本 SQL、建表生成器、重复结构清单、Word 提取文本和 Python 缓存。字段字典保留在 [MySQL 数据字典](../docs/mysql_data_dictionary.md)，原始需求与设计文档保留在 `docs/`。

## 配置与运行

需要 Python 3.12。Windows 开发环境使用独立 Conda 环境：

```powershell
conda create -n AI_BI_Platform python=3.12 pip -y
conda activate AI_BI_Platform
python -m pip install -r infrastructure/requirements.txt
```

首次环境复制 `.env.example` 为 `.env`，填入 MySQL 连接密码。现有 `.env` 不会被初始化脚本覆盖。MySQL 使用本机服务，配置不再包含未使用的 MYSQL_ROOT_PASSWORD。

从项目根目录执行：

```powershell
./infrastructure/scripts/Initialize-Database.ps1 -Target MySQL -Test
python infrastructure/scripts/db.py status
```

入口检查当前环境为 Python 3.12，并在缺少 PyMySQL 时安装到当前环境；随后初始化数据库、配置运行角色并执行约束回归。不创建共享管理员、业务租户或试用价格。

ClickHouse 使用 Docker Desktop 的 Linux 容器。填入非空 CLICKHOUSE_PASSWORD 后：

```powershell
./infrastructure/scripts/Initialize-Database.ps1 -Target ClickHouse -StartClickHouse -Test
```

使用已有实例时填写 CLICKHOUSE_HTTP_URL、CLICKHOUSE_USER、CLICKHOUSE_PASSWORD，并去掉 `-StartClickHouse`。Compose 仅启动 ClickHouse，使用持久卷、健康检查与 127.0.0.1 端口绑定。不要使用 `down -v` 清理业务数据。远程 ClickHouse 连接应使用 HTTPS。

## 初始化与已落库环境

MySQL 首次执行会创建配置指定的库以及 `infrastructure_schema_history`，再执行最终 SQL。所有业务 DDL 与种子数据均来自 `sql/mysql.sql`；脚本只额外管理数据库和初始化历史。

已执行旧 V001—V008 的本机环境，脚本根据最终 SQL 文件头保存的旧校验和、完整成功记录及表/外键清单确认兼容，只登记 `BASELINE_001`，不重建业务表、不重新插入权限码。旧记录保留在数据库中用于审计，项目不再保存旧 SQL 文件。以后重复运行检查最终文件 SHA-256，一致则跳过。

无可识别历史的非空库、失败记录、文件校验和变化都停止执行。MySQL DDL 隐式提交，失败需检查实际已执行语句并前滚修复，不能删除失败记录后盲目重跑。最终 SQL 是初始化快照，不是任意已有库的覆盖脚本；今后结构变更需另行设计升级方案，禁止直接修改最终 SQL 后向现有库重放。

ClickHouse 基线使用 `IF NOT EXISTS`，成功后在 `ai_bi_ops.schema_history` 记录校验和。中断后可补建缺失对象；既有对象的列类型等仍需核对 `SHOW CREATE TABLE`，`IF NOT EXISTS` 不代表结构一致。迁移锁依赖 MySQL，执行 ClickHouse 初始化时 MySQL 也必须可连接。

## 数据模型与运行权限

- MySQL 使用 InnoDB、utf8mb4_0900_as_cs、UTC DATETIME(3) 和应用生成的 BIGINT UNSIGNED 主键。email 由应用统一标准化。
- 租户实体均有 `(tenant_id,id)` 唯一键与复合外键，父实体删除 RESTRICT，软删后业务编码保持占用。
- 当前语义版本必须属于同一资产；发布批次和物理指针的数据集、代、序号相互匹配。工作空间拥有者及其 membership 在同事务创建。
- 金额使用有符号 BIGINT 最小货币单位；配额余额受 CHECK 保护。支付渠道事件在全库唯一，AI 供应商请求键包含 provider_code。
- `aibi_api_role`、`aibi_worker_role` 按表授权，不授予 DDL；审计、账本和不可变版本限制 SELECT/INSERT。由管理员为应用创建独立登录账号、授予角色并 SET DEFAULT ROLE，不能把 root 用作应用账号。
- 角色脚本只增加明确列出的授权，不清除既有额外授权；已有角色需要单独审计。字段定义以最终 SQL 为准，权限脚本与测试直接读取 SQL 表清单，无第二份生成模型。

原始数据库文档提到的 SQL 附件未在项目中提供；当前字段和约束是依据正文补齐的实现。379 个外键包括通用审计用户、租户根引用和强化的资产归属约束，因此不同于原文附件统计的 183 个。

## 服务层必须实现的事务规则

**批次发布：** Worker 固定可信租户、数据集、lease_owner/fencing_token，在承载查询的 ClickHouse 副本校验行数、hash、金额等。MySQL 事务按固定顺序锁定运行、批次、发布指针与水位，核验租约有效、令牌一致、批次 READY、质量 PASSED。增量必须同代且发布序号加一，全量必须新代且序号为一。

按 `tenant_id + dataset_id + lock_version` 条件更新发布指针并递增 lock_version，要求 affected_rows=1；与批次 PUBLISHED、水位推进及 Outbox 同事务提交。首次空指针的 active_generation/last_batch_id 为 NULL、committed_publish_seq 为 0。禁止跳号及直接人工推进水位。

**配额预留：** 同事务锁定余额，检查 business_event_key 及请求哈希；条件更新要求 `amount > 0 AND used_amount + reserved_amount + amount <= quota_limit`。更新成功再写唯一 RESERVE 流水，任何冲突均回滚。确认/释放按 reservation_key 核验剩余预留额度，在余额锁内处理，每个阶段使用独立幂等键；确认与释放总和不能超过原预留额。

**查询与授权：** 固定语义版本和物理发布指针，先筛选 tenant/dataset/generation/committed_seq，再按业务键 argMax，最后执行当前属性的 RLS、逻辑删除、业务时间与指标过滤。查询示例与退款/取消样例集中在 `tests/test_clickhouse.py`。读副本不完整返回 DATA_NOT_READY，不能只凭收据存在交付结果。DWS 仅使用已验证 snapshot_id，不能直接对原始修订事件 SUM。

多态授权主体归属、scope_key 与 workspace_id 一致、租户至少一名有效所有者、部门/指标 DAG 无环、SCD2 区间不重叠、JSON AST 校验与撤权重鉴权仍由服务层完成。支付回调必须验签并核验订单、金额、币种，不能根据浏览器跳转确认收款。

## 版本与验证

记录日期：2026-10-09（Asia/Shanghai）。

| 项目 | 状态 |
|---|---|
| 本机 MySQL Community 8.0.43 | ai_bi_core 已落库；沿用现有服务 |
| Python 3.10.11 / PyMySQL 1.1.2 | 本机执行通过 |
| MySQL 8.4 LTS | 设计建议版本，本次未升级或验证 |
| ClickHouse 25.8.4.13 | 已固定开发镜像 tag，尚未拉取或运行；digest 待验证 |
| MySQL 约束回归 | 13 项通过：租户引用、版本归属、批次、配额、幂等、软删、RESTRICT 等 |
| 最终基线 | 已验证旧库接管、重复跳过、独立空库初始化及失败/校验和保护 |
| ClickHouse | 4 库/8 表静态检查；真库与分析 SQL 测试尚未执行 |

```powershell
python infrastructure/tests/test_mysql.py
python infrastructure/tests/test_initialization.py
python infrastructure/tests/test_clickhouse.py
```

MySQL 约束测试均事务回滚；初始化测试和 ClickHouse 测试使用独立随机临时库并清理。未完成的应用端到端授权、并发发布与配额、读副本故障、备份恢复、性能和生产补丁验证不属于本次已通过项。RAW 的 30 天 TTL 仅为开发样例，其余事件不擅自设置保留期限。
