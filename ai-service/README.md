# AI 智能分析服务

该服务只接受 Spring Boot 通过 HMAC 短时委托提交的内部任务。它负责 LangGraph 编排、澄清中断、QueryPlan 规划、受控 Java 工具调用、结果校验、ChartSpec 和中文回答；不直接连接 MySQL 或 ClickHouse。

本地启动：

```powershell
python -m venv .venv
.\.venv\Scripts\pip install -e ".[test]"
$env:AI_SHARED_SECRET="replace-with-at-least-32-random-characters"
.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8091
```

生产环境把 `AI_CHECKPOINT_BACKEND` 设为 `redis`，使用独立、不可淘汰、开启 AOF/副本和备份的 Checkpoint Redis。服务默认拒绝过期、重放、越权工具和超时任务。

