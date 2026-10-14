from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _integer(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value < 1:
        raise ValueError(f"{name} 必须大于 0")
    return value


@dataclass(frozen=True, slots=True)
class Settings:
    environment: str
    bind_host: str
    bind_port: int
    java_base_url: str
    shared_secret: str
    checkpoint_backend: str
    checkpoint_sqlite_path: Path
    checkpoint_redis_url: str | None
    task_db_path: Path
    total_timeout_seconds: int
    node_timeout_seconds: int
    max_tool_calls: int
    max_workers: int

    @classmethod
    def load(cls) -> "Settings":
        environment = os.getenv("AI_ENV", "local").lower()
        secret = os.getenv("AI_SHARED_SECRET", "local-development-secret-change-me")
        # 开发默认值长度也超过 32，生产必须显式配置非默认密钥。
        if environment == "production" and (
            len(secret.strip()) < 32 or secret == "local-development-secret-change-me"
        ):
            raise ValueError("生产环境 AI_SHARED_SECRET 必须显式配置为至少 32 字符的非默认密钥")
        backend = os.getenv("AI_CHECKPOINT_BACKEND", "sqlite").lower()
        if backend not in {"sqlite", "redis"}:
            raise ValueError("AI_CHECKPOINT_BACKEND 仅支持 sqlite 或 redis")
        redis_url = os.getenv("AI_CHECKPOINT_REDIS_URL")
        if environment == "production" and backend != "redis":
            raise ValueError("生产环境必须使用独立持久化 Redis Checkpointer")
        if backend == "redis" and not redis_url:
            raise ValueError("Redis Checkpointer 缺少 AI_CHECKPOINT_REDIS_URL")
        return cls(
            environment=environment,
            bind_host=os.getenv("AI_BIND_HOST", "127.0.0.1"),
            bind_port=_integer("AI_BIND_PORT", 8091),
            java_base_url=os.getenv("AI_JAVA_BASE_URL", "http://127.0.0.1:8080").rstrip("/"),
            shared_secret=secret,
            checkpoint_backend=backend,
            checkpoint_sqlite_path=Path(os.getenv("AI_CHECKPOINT_SQLITE_PATH", "var/checkpoints.sqlite3")),
            checkpoint_redis_url=redis_url,
            task_db_path=Path(os.getenv("AI_TASK_DB_PATH", "var/tasks.sqlite3")),
            total_timeout_seconds=_integer("AI_TOTAL_TIMEOUT_SECONDS", 60),
            node_timeout_seconds=_integer("AI_NODE_TIMEOUT_SECONDS", 12),
            max_tool_calls=_integer("AI_MAX_TOOL_CALLS", 8),
            max_workers=_integer("AI_MAX_WORKERS", 4),
        )

