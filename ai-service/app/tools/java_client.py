from __future__ import annotations

import hashlib
import json
import time
import uuid
from typing import Any

import httpx

from app.core.errors import AiEngineError
from app.persistence.task_store import TaskStore
from app.schemas.models import DelegatedContext, QueryPlan, QueryResult, SemanticCatalog
from app.security.delegation import DelegationSigner


class JavaToolClient:
    def __init__(
        self,
        base_url: str,
        secret: str,
        store: TaskStore,
        timeout_seconds: int = 12,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = base_url
        self._signer = DelegationSigner(secret)
        self._store = store
        self._client = httpx.Client(
            base_url=base_url, timeout=httpx.Timeout(timeout_seconds), transport=transport
        )

    def catalog_search(
        self, context: DelegatedContext, question: str, dataset_id: int | None
    ) -> SemanticCatalog:
        self._require_tool(context, "catalog_search")
        payload = {"task_id": context.task_id, "question": question,
                   "dataset_id": None if dataset_id is None else str(dataset_id)}
        data = self._call(context, "catalog_search", "/internal/v1/semantic/authorized", payload, 1)
        return SemanticCatalog.model_validate(data)

    def validate(self, context: DelegatedContext, plan: QueryPlan, call_seq: int) -> dict[str, Any]:
        self._require_tool(context, "query_validate")
        payload = {"task_id": context.task_id, "plan": plan.model_dump(mode="json")}
        return self._call(context, "query_validate", "/internal/v1/queries/validate", payload, call_seq)

    def execute(self, context: DelegatedContext, plan: QueryPlan, call_seq: int) -> QueryResult:
        self._require_tool(context, "query_execute")
        payload = {"task_id": context.task_id, "plan": plan.model_dump(mode="json")}
        data = self._call(context, "query_execute", "/internal/v1/queries/execute", payload, call_seq)
        return QueryResult.model_validate(data)

    def _call(
        self,
        context: DelegatedContext,
        tool_name: str,
        path: str,
        payload: dict[str, Any],
        call_seq: int,
    ) -> dict[str, Any]:
        key = f"{context.task_id}:{tool_name}:{call_seq}"
        signed_payload = {**payload, "tool_call_id": key}
        body = json.dumps(
            signed_payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        arguments_hash = hashlib.sha256(body).hexdigest()
        claimed, cached = self._store.claim_tool(context.task_id, key, tool_name, arguments_hash)
        if not claimed:
            if cached is not None:
                return cached
            # The previous HTTP response may have been lost after Java committed the
            # invocation. Reusing the same signed tool_call_id lets Java return the
            # authoritative cached result without running the query or billing again.
        timestamp = int(time.time())
        nonce = uuid.uuid4().hex
        headers = {
            "Content-Type": "application/json",
            "X-AI-Timestamp": str(timestamp),
            "X-AI-Nonce": nonce,
            "X-AI-Signature": self._signer.sign("POST", path, body, timestamp, nonce),
            "X-Trace-Id": context.trace_id,
            "Idempotency-Key": key,
        }
        try:
            response = self._client.post(path, content=body, headers=headers)
            response.raise_for_status()
            envelope = response.json()
            if isinstance(envelope, dict) and "code" in envelope:
                if envelope.get("code") != "SUCCESS":
                    raise AiEngineError(
                        str(envelope.get("code", "JAVA_TOOL_REJECTED")),
                        str(envelope.get("message", "Java 受控工具拒绝请求")),
                        response.status_code,
                    )
                result = envelope.get("data")
            else:
                result = envelope
            if not isinstance(result, dict):
                raise AiEngineError("JAVA_TOOL_INVALID_RESPONSE", "Java 工具响应格式无效", 502)
            self._store.finish_tool(context.task_id, key, result)
            return result
        except httpx.TimeoutException as exc:
            raise AiEngineError("JAVA_TOOL_TIMEOUT", "Java 受控查询工具超时", 504) from exc
        except httpx.HTTPError as exc:
            raise AiEngineError("JAVA_TOOL_UNAVAILABLE", "Java 受控查询工具不可用", 502) from exc

    @staticmethod
    def _require_tool(context: DelegatedContext, name: str) -> None:
        if name not in context.allowed_tools:
            raise AiEngineError("TOOL_NOT_DELEGATED", f"未授权工具: {name}", 403)

