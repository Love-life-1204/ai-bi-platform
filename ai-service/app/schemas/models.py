from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Any, Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator, model_validator


from app.schemas.resource_ids import ResourceId


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class DelegatedContext(StrictModel):
    issuer: str = Field(min_length=1, max_length=64)
    audience: Literal["ai-bi-engine"]
    jti: str = Field(min_length=16, max_length=128)
    # 在模型入口拒绝无时区时间，避免比较时异常或跨时区口径漂移。
    expires_at: AwareDatetime
    deadline_at: AwareDatetime
    tenant_id: ResourceId = Field(gt=0)
    member_id: ResourceId = Field(gt=0)
    workspace_id: ResourceId = Field(gt=0)
    user_id: ResourceId = Field(gt=0)
    session_id: ResourceId = Field(gt=0)
    tenant_permission_version: int = Field(gt=0)
    member_permission_version: int = Field(gt=0)
    context_version: int = Field(ge=0)
    request_id: str = Field(min_length=8, max_length=128)
    task_id: str = Field(min_length=1, max_length=128)
    conversation_id: str = Field(min_length=1, max_length=128)
    thread_id: str = Field(min_length=16, max_length=255)
    trace_id: str = Field(min_length=8, max_length=128)
    allowed_tools: frozenset[Literal["catalog_search", "query_validate", "query_execute"]]

    @model_validator(mode="after")
    def validate_windows(self) -> "DelegatedContext":
        if self.deadline_at > self.expires_at:
            raise ValueError("deadline_at 不能晚于委托过期时间")
        return self


class TaskExecuteRequest(StrictModel):
    context: DelegatedContext
    question: str = Field(min_length=1, max_length=4000)
    dataset_id: ResourceId | None = Field(default=None, gt=0)
    confirmed_context_version: int | None = Field(default=None, ge=0)

    @field_validator("question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        cleaned = " ".join(value.strip().split())
        if not cleaned:
            raise ValueError("问题不能为空")
        return cleaned


class TaskResumeRequest(StrictModel):
    context: DelegatedContext
    clarification: dict[str, str | int | float | bool]


class SemanticField(StrictModel):
    field_id: ResourceId = Field(gt=0)
    key: str
    name: str
    data_type: str
    role: Literal["DIMENSION", "MEASURE", "TIME"]


class SemanticMetric(StrictModel):
    metric_version_id: ResourceId = Field(gt=0)
    metric_id: ResourceId = Field(gt=0)
    dataset_version_id: ResourceId = Field(gt=0)
    code: str
    name: str
    unit: str | None = None
    aliases: list[str] = Field(default_factory=list, max_length=50)
    allowed_dimension_field_ids: list[ResourceId] = Field(default_factory=list, max_length=50)
    time_field_id: ResourceId | None = Field(default=None, gt=0)


class SemanticDataset(StrictModel):
    dataset_id: ResourceId = Field(gt=0)
    dataset_version_id: ResourceId = Field(gt=0)
    code: str
    name: str
    fields: list[SemanticField] = Field(default_factory=list, max_length=200)


class SemanticCatalog(StrictModel):
    datasets: list[SemanticDataset] = Field(default_factory=list, max_length=50)
    metrics: list[SemanticMetric] = Field(default_factory=list, max_length=200)
    terms: list[dict[str, Any]] = Field(default_factory=list, max_length=200)
    semantic_version: str


class TimeRange(StrictModel):
    field_id: ResourceId = Field(gt=0)
    start: AwareDatetime
    end: AwareDatetime

    @model_validator(mode="after")
    def ordered(self) -> "TimeRange":
        if self.start >= self.end:
            raise ValueError("时间范围必须满足 start < end")
        return self


class QueryFilter(StrictModel):
    field_id: ResourceId = Field(gt=0)
    operator: Literal["EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"]
    value: Any = None


class QuerySort(StrictModel):
    target_type: Literal["FIELD", "METRIC"]
    target_id: ResourceId = Field(gt=0)
    direction: Literal["ASC", "DESC"]


class QueryPlan(StrictModel):
    dataset_version_id: ResourceId = Field(gt=0)
    metric_version_ids: list[ResourceId] = Field(min_length=1, max_length=20)
    dimension_field_ids: list[ResourceId] = Field(default_factory=list, max_length=20)
    time_range: TimeRange | None = None
    filters: list[QueryFilter] = Field(default_factory=list, max_length=50)
    sort: list[QuerySort] = Field(default_factory=list, max_length=10)
    limit: int = Field(default=500, ge=1, le=10_000)

    @model_validator(mode="after")
    def reject_duplicates(self) -> "QueryPlan":
        if len(set(self.metric_version_ids)) != len(self.metric_version_ids):
            raise ValueError("指标版本不能重复")
        if len(set(self.dimension_field_ids)) != len(self.dimension_field_ids):
            raise ValueError("维度字段不能重复")
        return self


class QueryColumn(StrictModel):
    key: str
    label: str
    data_type: str
    kind: Literal["DIMENSION", "METRIC"]


class PublicationSnapshot(StrictModel):
    generation_id: ResourceId
    committed_publish_seq: int
    quality_version: int


class QueryResult(StrictModel):
    query_id: str
    status: Literal["SUCCEEDED"]
    columns: list[QueryColumn]
    rows: list[dict[str, Any]] = Field(max_length=10_000)
    row_count: int = Field(ge=0)
    cached: bool = False
    publication: PublicationSnapshot
    execution_ms: int = Field(ge=0)


class ChartSpec(StrictModel):
    type: Literal["KPI", "BAR", "LINE", "PIE", "TABLE"]
    category_field: str | None = None
    value_fields: list[str] = Field(default_factory=list, max_length=20)
    title: str = Field(max_length=200)


class Fact(StrictModel):
    statement: str
    value: Decimal | str | int | float | None = None
    unit: str | None = None
    metric_version_id: ResourceId | None = None
    query_id: str


class Answer(StrictModel):
    summary: str
    facts: list[Fact]
    assumptions: list[str]
    recommendations: list[str]
    limitations: list[str]


class TaskAccepted(StrictModel):
    request_id: str
    task_id: str
    status: str


class TaskEvent(StrictModel):
    event_id: str
    sequence: int = Field(gt=0)
    task_id: str
    trace_id: str
    type: Literal["accepted", "progress", "clarification", "result", "error", "done"]
    emitted_at: datetime
    payload: dict[str, Any]


class TaskStatus(StrictModel):
    task_id: str
    status: str
    last_sequence: int
    error_code: str | None = None
    clarification: dict[str, Any] | None = None
    result: dict[str, Any] | None = None

