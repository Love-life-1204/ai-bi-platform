# RabbitMQ 实施与验收记录（2026-10-14）

## 已部署环境

RabbitMQ 已部署到远程 Docker 主机，运行镜像为 `rabbitmq:4.2.9-management`，镜像摘要为
`sha256:9e9fd21d60f060acc9ecbd847cd1dcb87e6550bdb0297c27e79a40bfe1766ce4`。
容器名和节点主机名均为 `ai-bi-rabbitmq`，重启策略为 `unless-stopped`，健康检查已通过。

AMQP 只监听 TLS 端口 5671；远程宿主机仅发布 `127.0.0.1:5671` 和
`127.0.0.1:15672`。两者均不能从公网直接访问，本机通过 SSH 本地转发连接。
管理端口在 SSH 隧道内使用 HTTP，消息协议本身使用 TLS 1.2/1.3。RabbitMQ 数据保存到
Docker 命名卷 `ai-bi-rabbitmq-data`。Docker 服务已设置为随系统启动。

本次使用服务器证书校验、RabbitMQ 用户密码和 SSH 隧道三层保护；没有启用客户端证书。
生产交付前应把 SSH 登录从 root 密码切换为无 sudo 权限的专用隧道用户和密钥，并轮换本次使用的
SSH/RabbitMQ 初始凭据。

## 拓扑与权限

Virtual Host 为 `/aibi`。管理员账号只用于部署和运维；应用账号无任何管理标签，其权限为：

- configure：`^aibi\..*$`
- write：`^(aibi\.tasks|aibi\.dlx)$`
- read：`^aibi\.(sync|import|export|ai)\.(q|dlq)$`

业务拓扑如下。主队列和死信队列均为 durable quorum queue，主队列设置
`x-delivery-limit=5` 并路由到独立 DLQ。

| 任务 | Exchange / Routing Key | 主队列 | DLQ Routing Key / 队列 | 消费者 |
|---|---|---|---|---|
| 数据同步 | `aibi.tasks` / `task.sync` | `aibi.sync.q` | `dead.sync` / `aibi.sync.dlq` | `sync-worker` |
| 文件导入 | `aibi.tasks` / `task.import` | `aibi.import.q` | `dead.import` / `aibi.import.dlq` | `import-worker` |
| 数据导出 | `aibi.tasks` / `task.export` | `aibi.export.q` | `dead.export` / `aibi.export.dlq` | `export-worker` |
| AI 任务 | `aibi.tasks` / `task.ai` | `aibi.ai.q` | `dead.ai` / `aibi.ai.dlq` | `ai-worker` |

## 应用实现

Spring Boot 使用 `spring-boot-starter-amqp`，并通过 `RABBITMQ_ENABLED` 开关在本地直执模式和
RabbitMQ 模式之间切换。RabbitMQ 模式具备：

- MySQL 业务写入与 `sys_outbox_event` 同事务提交；发布器使用租约、`SKIP LOCKED`、
  Publisher Confirms、mandatory returns、持久消息和有限指数退避。
- Outbox 候选批次按租户轮询，保持单租户内部顺序，并以批次大小、Prefetch 和最大消费者并发
  形成有界背压。该策略提供工作保守型公平分发，不是按租户权重或 SLA 的严格队列调度器。
- 消费者在同一 MySQL 事务中先写 `sys_inbox_dedup` 再执行业务处理；唯一键
  `(tenant_id, consumer_code, event_key)` 防止重复执行业务副作用。
- 消费者采用手动 ACK，只有 Inbox/业务事务成功后才确认；失败进行 3 次指数退避重试，最终
  reject 且不 requeue，由 DLX 进入任务类型对应的 DLQ。
- 同步、导入、导出和 AI 四类任务均已接入。AI 消息携带可选 `datasetId`，消费者重建任务时不会
  丢失数据集上下文。

默认参数为 Prefetch 4、初始并发 1、最大并发 4、发布批次 50。参数均可通过
`application.yml` 的 `app.messaging` 配置调整。

## 部署和连接

先复制并填写凭据模板，文件已被 Git 忽略：

```powershell
Copy-Item infrastructure/docker/rabbitmq/.env.example infrastructure/docker/rabbitmq/.env
./infrastructure/scripts/Deploy-RabbitMqRemote.ps1 -SshHost <remote-host>
```

部署脚本上传 Compose、RabbitMQ 配置和引导脚本，生成私有 CA/服务器证书，启动容器，创建最小权限
账号和拓扑，并下载 CA。客户端 PKCS12 信任库由本机 `keytool` 生成，避免生成 Java 无法识别的
空证书库。

日常开发连接：

```powershell
./infrastructure/scripts/Start-RabbitMqTunnel.ps1 -SshHost <remote-host>
```

另一个终端加载 `backend/.env.rabbitmq.local`，把 `RABBITMQ_ENABLED` 设置为 `true` 后启动后端。
管理界面只在隧道运行时通过 `http://localhost:15672` 访问。

## DLQ 人工重投

重投前先处理根因并记录工单。通过 SSH 隧道进入管理界面，以管理员账号查看对应 DLQ：

1. 使用 requeue 模式取一条消息，记录 `message_id`、原 routing key、payload、失败头和处理原因；
2. 使用原 `message_id` 向 `aibi.tasks` 和对应的 `task.*` routing key 发布持久消息；
3. 确认新消息已路由后，再从 DLQ ACK 原消息；
4. 小批量执行并观察主队列、DLQ、消费失败率和 MySQL Inbox。即使重投步骤中发生重复发布，
   Inbox 唯一键也会阻止相同消费者重复执行业务副作用。

禁止在未修复根因时批量清空 DLQ，也不要使用 `docker compose down -v`，后者会删除持久化卷。

## 验收结果

| 编号 | 结果 | 证据 |
|---|---|---|
| MQ-01 | 通过 | RabbitMQ 4.2.9，节点健康，TLS listener 5671，管理 listener 15672 |
| MQ-02 | 通过 | topic exchange、4 个任务 quorum queue、4 个 quorum DLQ 和独立 routing key 已创建 |
| MQ-03 | 通过 | Docker 健康检查、重启策略、命名卷和 Docker 开机启动已验证 |
| MQ-04 | 通过 | `/aibi`、无管理标签应用用户、最小权限、TLS、仅回环端口和 SSH 隧道 |
| MQ-05 | 通过 | durable quorum queue、delivery mode 2、Publisher Confirm 和 mandatory return |
| MQ-06 | 通过 | Spring AMQP 连接、拓扑、Outbox 发布器和 4 个消费者已实现 |
| MQ-07 | 通过 | MySQL Outbox + Inbox 唯一键；重复消息单元测试只执行一次 handler |
| MQ-08 | 通过 | 同步、导入、导出、AI 任务均通过 `TaskDispatcher` 接入 |
| MQ-09 | 通过 | 手动 ACK、Prefetch 4、消费者并发 1—4 |
| MQ-10 | 通过 | 3 次有限重试、指数退避、DLQ 和人工重投流程 |
| MQ-11 | 通过 | TLS 应用账号真实发布、路由、Confirm、消费和 ACK 测试通过 |
| MQ-12 | 通过 | 同一 `message_id` 重复投递成功；Inbox 幂等测试 handler 仅运行一次 |
| MQ-13 | 通过 | 未 ACK 连接关闭后 redelivered=true；Broker 重启后持久消息恢复并消费 |
| MQ-14 | 通过（有界公平） | Outbox 按租户轮询；4 队列隔离、Prefetch/并发边界和 10 租户混合消息验证通过 |
| MQ-15 | 通过（可靠性烟测） | 400 条混合持久消息全部路由并排空；重启恢复另行验证通过 |

测试记录：

- `MessagingReliabilityTest`：4 项通过，覆盖拓扑、直执顺序、Outbox 租户轮询和 Inbox 幂等。
- `RabbitMqLiveAcceptanceTest`：2 项通过，覆盖 TLS、Confirm、持久性、手动 ACK、redelivery、
  重复投递、DLQ，以及 400 条四队列/十租户混合负载。
- 完整后端回归：119 项执行，0 failure，0 error，3 项按环境条件跳过。

400 条测试通过 SSH 隧道逐条拉取消费，用于验证可靠性和路由隔离，不代表容量上限。正式容量结论仍需
在与生产相同规格、RTT、消息大小和消费者耗时下，执行持续压测并记录吞吐、P95/P99、内存、磁盘、
队列增长率与恢复时间。

