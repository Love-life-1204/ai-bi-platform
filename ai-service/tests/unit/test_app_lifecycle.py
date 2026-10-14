from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


def test_sqlite_checkpointer_lifecycle(tmp_path) -> None:
    settings = Settings(
        environment="test",
        bind_host="127.0.0.1",
        bind_port=8091,
        java_base_url="http://127.0.0.1:8080",
        shared_secret="test-shared-secret",
        checkpoint_backend="sqlite",
        checkpoint_sqlite_path=tmp_path / "checkpoints.sqlite3",
        checkpoint_redis_url=None,
        task_db_path=tmp_path / "tasks.sqlite3",
        total_timeout_seconds=60,
        node_timeout_seconds=2,
        max_tool_calls=8,
        max_workers=1,
    )
    with TestClient(create_app(settings)) as client:
        assert client.get("/health/live").json() == {"status": "UP"}
        assert client.get("/health/ready").json() == {"status": "UP"}

