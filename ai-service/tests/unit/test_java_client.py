from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import httpx
import pytest

from app.core.errors import AiEngineError
from app.persistence.task_store import TaskStore
from app.schemas.models import DelegatedContext, QueryPlan
from app.tools.java_client import JavaToolClient


def delegated_context() -> DelegatedContext:
    now = datetime.now(UTC)
    return DelegatedContext(
        issuer="java", audience="ai-bi-engine", jti="j" * 20,
        expires_at=now + timedelta(minutes=2), deadline_at=now + timedelta(minutes=1),
        tenant_id=1, member_id=2, workspace_id=3, user_id=4, session_id=5,
        tenant_permission_version=1, member_permission_version=1, context_version=0,
        request_id="request-0001", task_id="1001", conversation_id="2001",
        thread_id="thread-000000000001", trace_id="trace-0001",
        allowed_tools=frozenset({"query_execute"}),
    )


def test_lost_response_retry_reuses_authoritative_tool_call(tmp_path: Path) -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if len(requests) == 1:
            raise httpx.ReadTimeout("response lost after commit", request=request)
        return httpx.Response(200, json={
            "code": "SUCCESS",
            "data": {
                "query_id": "query-1001",
                "status": "SUCCEEDED",
                "columns": [{
                    "key": "net_sales", "label": "净销售额",
                    "data_type": "DECIMAL", "kind": "METRIC",
                }],
                "rows": [{"net_sales": "1200.00"}],
                "row_count": 1,
                "cached": True,
                "publication": {
                    "generation_id": 7,
                    "committed_publish_seq": 9,
                    "quality_version": 2,
                },
                "execution_ms": 12,
            },
        })

    client = JavaToolClient(
        "http://java.test", "s" * 32, TaskStore(tmp_path / "tasks.sqlite3"),
        transport=httpx.MockTransport(handler),
    )
    context = delegated_context()
    plan = QueryPlan(dataset_version_id=101, metric_version_ids=[201])

    with pytest.raises(AiEngineError) as lost_response:
        client.execute(context, plan, 2)
    assert lost_response.value.code == "JAVA_TOOL_TIMEOUT"
    result = client.execute(context, plan, 2)

    expected_key = "1001:query_execute:2"
    assert result.query_id == "query-1001"
    assert len(requests) == 2
    assert {request.headers["Idempotency-Key"] for request in requests} == {expected_key}
    assert {json.loads(request.content)["tool_call_id"] for request in requests} == {expected_key}
    assert requests[0].content == requests[1].content
