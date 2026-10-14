from __future__ import annotations

import sqlite3
from contextlib import AbstractContextManager
from pathlib import Path
from typing import Any


class CheckpointerHandle(AbstractContextManager[Any]):
    def __init__(self, backend: str, sqlite_path: Path, redis_url: str | None) -> None:
        self._backend = backend
        self._sqlite_path = sqlite_path
        self._redis_url = redis_url
        self._resource: Any = None
        self._context: Any = None

    def __enter__(self) -> Any:
        if self._backend == "redis":
            try:
                from langgraph.checkpoint.redis import RedisSaver
            except ImportError as exc:
                raise RuntimeError("Redis Checkpointer 依赖未安装，请安装项目的 redis 可选依赖") from exc
            self._context = RedisSaver.from_conn_string(self._redis_url)
            self._resource = self._context.__enter__()
            self._resource.setup()
            return self._resource
        from langgraph.checkpoint.sqlite import SqliteSaver

        self._sqlite_path.parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(self._sqlite_path, check_same_thread=False)
        self._resource = SqliteSaver(connection)
        return self._resource

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> bool | None:
        if self._context is not None:
            return self._context.__exit__(exc_type, exc, traceback)
        connection = getattr(self._resource, "conn", None)
        if connection is not None:
            connection.close()
        return None

