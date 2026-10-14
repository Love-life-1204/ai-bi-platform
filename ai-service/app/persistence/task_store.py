from __future__ import annotations

import json
import sqlite3
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from app.schemas.models import TaskEvent, TaskStatus


TERMINAL = {"SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "TIMEOUT"}


class TaskStore:
    """Small authoritative worker ledger; Java remains the user-facing task owner."""

    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        with self._connection:
            self._connection.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS task (
                  task_id TEXT PRIMARY KEY,
                  request_id TEXT NOT NULL,
                  trace_id TEXT NOT NULL,
                  thread_id TEXT NOT NULL UNIQUE,
                  status TEXT NOT NULL,
                  request_json TEXT NOT NULL,
                  clarification_json TEXT,
                  result_json TEXT,
                  error_code TEXT,
                  cancel_requested INTEGER NOT NULL DEFAULT 0,
                  updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS task_event (
                  task_id TEXT NOT NULL,
                  sequence INTEGER NOT NULL,
                  event_type TEXT NOT NULL,
                  payload_json TEXT NOT NULL,
                  emitted_at TEXT NOT NULL,
                  PRIMARY KEY (task_id, sequence)
                );
                CREATE TABLE IF NOT EXISTS tool_call (
                  task_id TEXT NOT NULL,
                  idempotency_key TEXT NOT NULL,
                  tool_name TEXT NOT NULL,
                  arguments_hash TEXT NOT NULL,
                  result_json TEXT,
                  status TEXT NOT NULL,
                  PRIMARY KEY (task_id, idempotency_key)
                );
                """
            )

    def create(self, task_id: str, request_id: str, trace_id: str, thread_id: str, request: dict[str, Any]) -> bool:
        with self._lock, self._connection:
            cursor = self._connection.execute(
                "INSERT OR IGNORE INTO task(task_id,request_id,trace_id,thread_id,status,request_json,updated_at) "
                "VALUES(?,?,?,?,?,?,?)",
                (task_id, request_id, trace_id, thread_id, "ACCEPTED", self._json(request), self._now()),
            )
            return cursor.rowcount == 1

    def update(
        self,
        task_id: str,
        status: str,
        *,
        clarification: dict[str, Any] | None = None,
        result: dict[str, Any] | None = None,
        error_code: str | None = None,
    ) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                "UPDATE task SET status=?,clarification_json=?,result_json=?,error_code=?,updated_at=? WHERE task_id=?",
                (
                    status,
                    self._json(clarification) if clarification is not None else None,
                    self._json(result) if result is not None else None,
                    error_code,
                    self._now(),
                    task_id,
                ),
            )

    def request_cancel(self, task_id: str) -> bool:
        with self._lock, self._connection:
            row = self._connection.execute("SELECT status FROM task WHERE task_id=?", (task_id,)).fetchone()
            if row is None or row["status"] in TERMINAL:
                return False
            self._connection.execute(
                "UPDATE task SET cancel_requested=1,status='CANCEL_REQUESTED',updated_at=? WHERE task_id=?",
                (self._now(), task_id),
            )
            return True

    def is_cancel_requested(self, task_id: str) -> bool:
        with self._lock:
            row = self._connection.execute(
                "SELECT cancel_requested FROM task WHERE task_id=?", (task_id,)
            ).fetchone()
            return bool(row and row["cancel_requested"])

    def append_event(self, task_id: str, trace_id: str, event_type: str, payload: dict[str, Any]) -> TaskEvent:
        with self._lock, self._connection:
            sequence = self._connection.execute(
                "SELECT COALESCE(MAX(sequence),0)+1 FROM task_event WHERE task_id=?", (task_id,)
            ).fetchone()[0]
            emitted = datetime.now(UTC)
            self._connection.execute(
                "INSERT INTO task_event(task_id,sequence,event_type,payload_json,emitted_at) VALUES(?,?,?,?,?)",
                (task_id, sequence, event_type, self._json(payload), emitted.isoformat()),
            )
            return TaskEvent(
                event_id=f"{task_id}:{sequence}", sequence=sequence, task_id=task_id,
                trace_id=trace_id, type=event_type, emitted_at=emitted, payload=payload,
            )

    def status(self, task_id: str) -> TaskStatus | None:
        with self._lock:
            row = self._connection.execute("SELECT * FROM task WHERE task_id=?", (task_id,)).fetchone()
            if row is None:
                return None
            last = self._connection.execute(
                "SELECT COALESCE(MAX(sequence),0) FROM task_event WHERE task_id=?", (task_id,)
            ).fetchone()[0]
            return TaskStatus(
                task_id=task_id, status=row["status"], last_sequence=last,
                error_code=row["error_code"],
                clarification=self._loads(row["clarification_json"]),
                result=self._loads(row["result_json"]),
            )

    def request(self, task_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._connection.execute("SELECT request_json FROM task WHERE task_id=?", (task_id,)).fetchone()
            return None if row is None else self._loads(row["request_json"])

    def recoverable_requests(self) -> list[dict[str, Any]]:
        """Return durable work that was accepted but not terminal before a restart."""
        with self._lock:
            rows = self._connection.execute(
                "SELECT request_json FROM task "
                "WHERE status IN ('ACCEPTED','RUNNING','CANCEL_REQUESTED') ORDER BY updated_at"
            ).fetchall()
            return [self._loads(row["request_json"]) for row in rows]

    def events(self, task_id: str, after: int = 0, limit: int = 100) -> list[TaskEvent]:
        with self._lock:
            trace = self._connection.execute("SELECT trace_id FROM task WHERE task_id=?", (task_id,)).fetchone()
            if trace is None:
                return []
            rows = self._connection.execute(
                "SELECT * FROM task_event WHERE task_id=? AND sequence>? ORDER BY sequence LIMIT ?",
                (task_id, after, min(max(limit, 1), 500)),
            ).fetchall()
            return [TaskEvent(
                event_id=f"{task_id}:{row['sequence']}", sequence=row["sequence"], task_id=task_id,
                trace_id=trace["trace_id"], type=row["event_type"],
                emitted_at=datetime.fromisoformat(row["emitted_at"]), payload=self._loads(row["payload_json"]),
            ) for row in rows]

    def claim_tool(self, task_id: str, key: str, tool_name: str, arguments_hash: str) -> tuple[bool, Any | None]:
        with self._lock, self._connection:
            row = self._connection.execute(
                "SELECT arguments_hash,result_json,status FROM tool_call WHERE task_id=? AND idempotency_key=?",
                (task_id, key),
            ).fetchone()
            if row is not None:
                if row["arguments_hash"] != arguments_hash:
                    raise ValueError("幂等键对应的工具参数发生变化")
                return False, self._loads(row["result_json"])
            self._connection.execute(
                "INSERT INTO tool_call(task_id,idempotency_key,tool_name,arguments_hash,status) VALUES(?,?,?,?,?)",
                (task_id, key, tool_name, arguments_hash, "RUNNING"),
            )
            return True, None

    def finish_tool(self, task_id: str, key: str, result: Any) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                "UPDATE tool_call SET status='SUCCEEDED',result_json=? WHERE task_id=? AND idempotency_key=?",
                (self._json(result), task_id, key),
            )

    @staticmethod
    def _json(value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)

    @staticmethod
    def _loads(value: str | None) -> Any:
        return None if value is None else json.loads(value)

    @staticmethod
    def _now() -> str:
        return datetime.now(UTC).isoformat()

