# 开发环境与项目基线验证记录

- 验证日期：2026-10-10（Asia/Shanghai）
- 项目根目录：`D:\1\Desktop\AI BI 智能数据分析\ai-bi-platform`
- 初始 Git 提交：`68ccf3d chore: initialize AI BI platform repository`
- Python 环境：`D:\Anaconda3\envs\AI_BI_Platform`

## 验证结果

| 编号 | 状态 | 结果 |
|---|---|---|
| 01 | 部分通过 | `frontend`、`backend`、`ai-service`、`infrastructure` 均存在；项目使用 `infrastructure` 而不是 `infra`。三个应用目录只有空目录结构，README 明确说明应用代码尚未初始化。 |
| 02 | 通过 | 已初始化 Git，默认分支为 `main`，并创建首次提交 `68ccf3d`。本地 `.env`、Python 缓存和 pytest 缓存均被忽略。 |
| 03 | 通过 | JDK/Javac 21.0.9 可用。 |
| 04 | 通过 | Maven 3.9.12 可用，并使用 JDK 21.0.9。 |
| 05 | 部分通过 | Node.js v24.15.0、npm 8.19.4 可用；因 `frontend/package.json` 不存在，无法判断项目 `engines` 要求或执行依赖安装、TypeScript 检查与构建。 |
| 06 | 通过 | 已在 `D:\Anaconda3\envs\AI_BI_Platform` 创建 Python 3.12.15 Conda 环境；PyMySQL 1.1.2 与 pytest 9.1.1 安装于该环境。脚本已改为使用当前 Conda 环境，不再优先加载 `infrastructure/.local/python`。 |
| 07 | 阻塞 | 未发现 Docker Desktop、Docker CLI 或 Docker Windows 服务，`docker compose` 无法执行；WSL 枚举也因系统权限被拒绝。 |
| 08 | 通过 | `.env.example` 和本地 `.env` 的 8 个必需键完整；保留可工作的既有 MySQL 凭据，并为 ClickHouse 生成 48 字节随机密码。本地 `.env` 已确认不会提交。 |
| 09 | 部分通过 | Compose 仅定义 ClickHouse。该服务包含健康检查、仅绑定 `127.0.0.1` 的 8123/9000 端口和两个命名卷；没有 MySQL、Redis、RabbitMQ、显式网络或 `depends_on`。相对“仅启动 ClickHouse”的现有设计正确，但不能承载完整基础设施清单。 |
| 10 | 通过（有告警） | MySQL 8.0.43 已在 127.0.0.1:3306 提供服务，`SELECT 1` 健康检查通过，查询到的运行时间为 861542 秒。Windows SCM 将 `MySQL80` 显示为 `StartPending`，与实际可连接状态不一致，后续应检查服务管理状态。 |
| 11 | 通过 | 项目没有 Flyway 依赖或 `flyway_schema_history` 工作流，实际使用 `infrastructure_schema_history` 和自定义基线脚本。执行迁移得到 `SKIP mysql.sql`（校验和一致）；79 张业务表、379 个外键、20 个权限码均验证通过。 |
| 12 | 部分通过 | ClickHouse DDL 静态验证通过：4 个数据库、8 张业务表；因 Docker/ClickHouse 实例不存在，未执行真库 DDL、结构对比和销售分析查询测试。 |
| 13 | 阻塞 | 未发现 Redis 或 RabbitMQ 服务，Compose 中也没有对应定义，无法启动或执行健康检查。 |
| 14 | 阻塞 | Maven 本身可用，但 `backend/pom.xml` 和 Java 源码不存在，无法编译或运行 Java 单元测试。 |
| 15 | 通过 | 在指定 Conda 环境中安装依赖并运行 pytest；MySQL 真库回归和初始化保护共 15 项全部通过。 |
| 16 | 阻塞 | `frontend/package.json`、TypeScript 配置和 Vue 源文件不存在，无法安装依赖、执行类型检查或 `npm run build`。 |
| 17 | 阻塞 | Spring Boot、FastAPI、Vue 应用入口和健康接口均不存在，无法启动应用服务。 |
| 18 | 部分通过 | 数据库层跨租户隔离、租户引用、配额、幂等、软删除和删除限制测试通过。登录、应用层销售查询、AI 演示及完整跨租户冒烟脚本不存在；ClickHouse 销售测试因运行环境缺失未执行。 |

## 已执行测试

```text
pytest infrastructure/tests/test_mysql.py infrastructure/tests/test_initialization.py
15 passed

MySQL 8.0.43
业务表：79
外键：379
健康检查：OK
```

## 继续完成阻塞项所需条件

1. 安装并启动 Docker Desktop，或提供可访问的 ClickHouse、Redis、RabbitMQ 实例。
2. 将 MySQL、Redis、RabbitMQ 纳入新的完整 Compose 设计，或明确继续使用本机/外部服务并补齐连接配置。
3. 初始化 `backend`、`ai-service`、`frontend` 的真实应用代码、构建清单、健康接口和冒烟测试。
4. 修复或重新注册 Windows `MySQL80` 服务的 SCM 状态，使服务状态与实际运行状态一致。
