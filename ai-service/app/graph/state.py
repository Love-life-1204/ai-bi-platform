from __future__ import annotations

from typing import Any, NotRequired, TypedDict

from app.schemas.models import Answer, ChartSpec, DelegatedContext, QueryPlan, QueryResult, SemanticCatalog


class GraphState(TypedDict):
    context: DelegatedContext
    question: str
    dataset_id: int | None
    intent: NotRequired[str]
    intent_confidence: NotRequired[float]
    catalog: NotRequired[SemanticCatalog]
    metric_candidates: NotRequired[list[int]]
    analysis_shape: NotRequired[str]
    confirmed_semantics: NotRequired[dict[str, Any]]
    candidate_plan: NotRequired[QueryPlan]
    validated_plan: NotRequired[QueryPlan]
    query_result: NotRequired[QueryResult]
    facts: NotRequired[list[dict[str, Any]]]
    chart_spec: NotRequired[ChartSpec | None]
    answer: NotRequired[Answer]
    tool_call_count: NotRequired[int]
    assumptions: NotRequired[list[str]]
    limitations: NotRequired[list[str]]

