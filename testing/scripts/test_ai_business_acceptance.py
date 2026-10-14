"""尚未通过的业务验收；保留正确预期，不能计入基础单元测试通过率。"""
from datetime import UTC, datetime
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "ai-service"), str(ROOT / "ai-service/tests/unit")]
from langgraph.checkpoint.memory import MemorySaver
import app.graph.builder as builder
from test_graph_recovery import FakeTools, catalog, context, services


def test_this_month_means_calendar_month_not_trailing_30_days(tmp_path, monkeypatch):
    class FixedClock(datetime):
        @classmethod
        def now(cls, tz=None):
            return datetime(2026, 10, 13, 12, tzinfo=UTC)
    monkeypatch.setattr(builder, "datetime", FixedClock)
    semantics = catalog(1)
    semantics.metrics[0].time_field_id = 13
    tools = FakeTools(semantics)
    graph_services, _ = services(tmp_path, tools)
    ctx = context().model_copy(update={
        "deadline_at": datetime(2026, 10, 14, tzinfo=UTC),
        "expires_at": datetime(2026, 10, 15, tzinfo=UTC),
    })
    result = builder.build_graph(MemorySaver(), graph_services).invoke(
        {"context":ctx, "question":"UTC 时区本月指标0是多少", "dataset_id":10},
        {"configurable":{"thread_id":ctx.thread_id}})
    assert result["validated_plan"].time_range.start == datetime(2026, 10, 1, tzinfo=UTC)
