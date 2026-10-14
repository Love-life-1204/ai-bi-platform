from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any, Callable

from langgraph.graph import END, START, StateGraph
from langgraph.types import interrupt

from app.schemas.resource_ids import parse_resource_id
from app.core.errors import AdmissionRejected, ModelFailure, TaskCancelled, TaskTimedOut
from app.graph.state import GraphState
from app.persistence.task_store import TaskStore
from app.providers.base import LanguageProvider
from app.retrieval.semantic_ranker import retrieve_metrics
from app.schemas.models import ChartSpec, DelegatedContext, Fact, QueryPlan, TimeRange
from app.tools.java_client import JavaToolClient


@dataclass(frozen=True, slots=True)
class GraphServices:
    tools: JavaToolClient
    store: TaskStore
    provider: LanguageProvider
    max_tool_calls: int
    progress: Callable[[DelegatedContext, str, str], None]


def build_graph(checkpointer: Any, services: GraphServices) -> Any:
    def guard(state: GraphState, stage: str) -> DelegatedContext:
        context = state["context"]
        now = datetime.now(UTC)
        if services.store.is_cancel_requested(context.task_id):
            raise TaskCancelled()
        if now >= context.deadline_at:
            raise TaskTimedOut()
        if now >= context.expires_at:
            raise AdmissionRejected("DELEGATION_EXPIRED", "任务委托已过期", 401)
        if context.audience != "ai-bi-engine":
            raise AdmissionRejected("DELEGATION_AUDIENCE_INVALID", "任务委托目标错误", 401)
        services.progress(context, stage, "RUNNING")
        return context

    def admission(state: GraphState) -> dict[str, Any]:
        context = guard(state, "admission")
        required = {"catalog_search", "query_validate", "query_execute"}
        if not required.issubset(context.allowed_tools):
            raise AdmissionRejected("DELEGATION_SCOPE_INSUFFICIENT", "任务委托工具范围不足", 403)
        if services.max_tool_calls < 2:
            raise AdmissionRejected("AI_QUOTA_EXCEEDED", "本次任务可用工具额度不足", 429)
        return {"tool_call_count": state.get("tool_call_count", 0), "assumptions": [], "limitations": []}

    def classify_intent(state: GraphState) -> dict[str, Any]:
        guard(state, "intent")
        try:
            intent, confidence = services.provider.classify_intent(state["question"])
        except Exception as exc:
            raise ModelFailure() from exc
        return {"intent": intent, "intent_confidence": confidence}

    def retrieve(state: GraphState) -> dict[str, Any]:
        context = guard(state, "retrieve")
        catalog = services.tools.catalog_search(context, state["question"], state.get("dataset_id"))
        if not catalog.datasets or not catalog.metrics:
            raise AdmissionRejected("NO_AUTHORIZED_SEMANTICS", "没有可用于回答该问题的已授权发布指标", 403)
        decision = retrieve_metrics(state["question"], catalog)
        candidates = [item.metric_version_id for item in decision.metrics]
        return {
            "catalog": catalog,
            "metric_candidates": candidates,
            "analysis_shape": decision.analysis_shape,
        }

    def resolve_ambiguity(state: GraphState) -> dict[str, Any]:
        context = guard(state, "clarify")
        if state.get("confirmed_semantics"):
            return {}
        catalog = state["catalog"]
        candidates = state.get("metric_candidates", [])
        if len(candidates) == 1:
            return {"confirmed_semantics": {"metric_version_ids": candidates}}
        if len(candidates) > 1 and state.get("analysis_shape") in {"COMPARE", "CORRELATE"}:
            selected = [
                metric for metric in catalog.metrics
                if metric.metric_version_id in candidates
            ]
            if len({metric.dataset_version_id for metric in selected}) == 1:
                return {"confirmed_semantics": {"metric_version_ids": candidates}}
        options = [
            {"metric_version_id": str(metric.metric_version_id), "name": metric.name, "unit": metric.unit}
            for metric in catalog.metrics[:10]
            if not candidates or metric.metric_version_id in candidates
        ]
        confirmation = interrupt({
            "code": "METRIC_AMBIGUOUS" if options else "METRIC_NOT_FOUND",
            "question": "请选择本次分析使用的业务指标。",
            "options": options,
            "context_version": context.context_version,
        })
        if not isinstance(confirmation, dict) or "metric_version_id" not in confirmation:
            raise AdmissionRejected("CLARIFICATION_INVALID", "澄清答案缺少 metric_version_id", 400)
        try:
            selected = parse_resource_id(confirmation["metric_version_id"])
        except ValueError as exc:
            raise AdmissionRejected("CLARIFICATION_INVALID", "澄清指标 ID 不合法", 400) from exc
        if selected not in {parse_resource_id(item["metric_version_id"]) for item in options}:
            raise AdmissionRejected("CLARIFICATION_OUT_OF_SCOPE", "澄清答案不在授权候选范围内", 403)
        return {"confirmed_semantics": {"metric_version_ids": [selected]}}

    def resume_guard(state: GraphState) -> dict[str, Any]:
        guard(state, "resume_guard")
        return {}

    def plan(state: GraphState) -> dict[str, Any]:
        guard(state, "plan")
        catalog = state["catalog"]
        semantics = state["confirmed_semantics"]
        metric_ids = semantics.get("metric_version_ids")
        if metric_ids is None and "metric_version_id" in semantics:
            metric_ids = [semantics["metric_version_id"]]
        metric_ids = [int(value) for value in metric_ids or []]
        metrics = [item for item in catalog.metrics if item.metric_version_id in metric_ids]
        if len(metrics) != len(metric_ids):
            raise AdmissionRejected("METRIC_VERSION_STALE", "确认的指标版本已不可用", 409)
        metric = metrics[0]
        if any(item.dataset_version_id != metric.dataset_version_id for item in metrics):
            raise AdmissionRejected("CROSS_DATASET_ANALYSIS_UNSUPPORTED", "复杂分析指标必须属于同一数据集版本", 400)
        dataset = next(
            (item for item in catalog.datasets if item.dataset_version_id == metric.dataset_version_id), None
        )
        if dataset is None:
            raise AdmissionRejected("DATASET_VERSION_STALE", "指标对应的数据集版本已不可用", 409)
        question = state["question"]
        dimensions = [
            field.field_id for field in dataset.fields
            if field.role == "DIMENSION"
            and all(field.field_id in item.allowed_dimension_field_ids for item in metrics)
            and field.name in question
        ][:5]
        time_range = None
        assumptions: list[str] = []
        if metric.time_field_id is not None and any(word in question for word in ("最近", "近30天", "本月", "趋势")):
            end = datetime.now(UTC)
            start = end - timedelta(days=30)
            time_range = TimeRange(field_id=metric.time_field_id, start=start, end=end)
            assumptions.append("问题未给出精确起止时间，按最近 30 个完整自然日历时间处理。")
        query_plan = QueryPlan(
            dataset_version_id=dataset.dataset_version_id,
            metric_version_ids=metric_ids,
            dimension_field_ids=dimensions,
            time_range=time_range,
            filters=[],
            sort=[],
            limit=500,
        )
        return {"candidate_plan": query_plan, "assumptions": assumptions}

    def preflight(state: GraphState) -> dict[str, Any]:
        context = guard(state, "preflight")
        count = state.get("tool_call_count", 0) + 1
        if count > services.max_tool_calls:
            raise AdmissionRejected("TOOL_CALL_LIMIT", "工具调用次数超过上限", 429)
        plan_value = state["candidate_plan"]
        plan_value = QueryPlan.model_validate(plan_value.model_dump(mode="json"))
        validation = services.tools.validate(context, plan_value, count)
        if not validation.get("valid", False):
            raise AdmissionRejected("QUERY_PLAN_REJECTED", "候选查询计划未通过 Java 安全预检", 400)
        return {"validated_plan": plan_value, "tool_call_count": count}

    def execute(state: GraphState) -> dict[str, Any]:
        context = guard(state, "query")
        count = state.get("tool_call_count", 0) + 1
        if count > services.max_tool_calls:
            raise AdmissionRejected("TOOL_CALL_LIMIT", "工具调用次数超过上限", 429)
        result = services.tools.execute(context, state["validated_plan"], count)
        return {"query_result": result, "tool_call_count": count}

    def verify_result(state: GraphState) -> dict[str, Any]:
        guard(state, "result_check")
        result = state["query_result"]
        if result.row_count != len(result.rows):
            raise AdmissionRejected("QUERY_RESULT_INCOMPLETE", "查询结果行数与返回内容不一致", 502)
        metric_columns = [column for column in result.columns if column.kind == "METRIC"]
        facts: list[dict[str, Any]] = []
        for row in result.rows:
            for column_index, column in enumerate(metric_columns):
                value = row.get(column.key)
                if value is None:
                    continue
                try:
                    number = Decimal(str(value))
                except InvalidOperation as exc:
                    raise AdmissionRejected("QUERY_RESULT_NON_NUMERIC", "指标列包含非数值结果", 502) from exc
                if not math.isfinite(float(number)):
                    raise AdmissionRejected("QUERY_RESULT_NON_FINITE", "指标列包含无穷或非法数值", 502)
                dimensions = [
                    f"{item.label}={row.get(item.key)}" for item in result.columns if item.kind == "DIMENSION"
                ]
                facts.append({
                    "statement": "、".join(dimensions) if dimensions else column.label,
                    "value": str(number),
                    "unit": None,
                    "metric_version_id": state["validated_plan"].metric_version_ids[
                        min(column_index, len(state["validated_plan"].metric_version_ids) - 1)
                    ],
                    "query_id": result.query_id,
                })
        limitations = [] if facts else ["查询成功，但当前筛选范围没有可用于回答的数值记录。"]
        return {"facts": facts, "limitations": limitations}

    def chart(state: GraphState) -> dict[str, Any]:
        guard(state, "chart")
        result = state["query_result"]
        metrics = [column.key for column in result.columns if column.kind == "METRIC"]
        dimensions = [column.key for column in result.columns if column.kind == "DIMENSION"]
        if not metrics:
            return {"chart_spec": None}
        if dimensions:
            chart_spec = ChartSpec(type="BAR", category_field=dimensions[0], value_fields=metrics, title="分析结果")
        else:
            chart_spec = ChartSpec(type="KPI", value_fields=metrics[:1], title="核心指标")
        return {"chart_spec": chart_spec}

    def narrate(state: GraphState) -> dict[str, Any]:
        guard(state, "answer")
        facts = [Fact.model_validate(item) for item in state.get("facts", [])]
        try:
            answer = services.provider.write_answer(
                state["question"], facts, state.get("assumptions", []), state.get("limitations", [])
            )
        except Exception as exc:
            raise ModelFailure() from exc
        return {"answer": answer}

    workflow = StateGraph(GraphState)
    workflow.add_node("admission", admission)
    workflow.add_node("intent", classify_intent)
    workflow.add_node("retrieve", retrieve)
    workflow.add_node("clarify", resolve_ambiguity)
    workflow.add_node("resume_guard", resume_guard)
    workflow.add_node("plan", plan)
    workflow.add_node("preflight", preflight)
    workflow.add_node("query", execute)
    workflow.add_node("result_check", verify_result)
    workflow.add_node("chart", chart)
    workflow.add_node("answer", narrate)
    workflow.add_edge(START, "admission")
    workflow.add_edge("admission", "intent")
    workflow.add_edge("intent", "retrieve")
    workflow.add_edge("retrieve", "clarify")
    workflow.add_edge("clarify", "resume_guard")
    workflow.add_edge("resume_guard", "plan")
    workflow.add_edge("plan", "preflight")
    workflow.add_edge("preflight", "query")
    workflow.add_edge("query", "result_check")
    workflow.add_edge("result_check", "chart")
    workflow.add_edge("chart", "answer")
    workflow.add_edge("answer", END)
    return workflow.compile(checkpointer=checkpointer)

