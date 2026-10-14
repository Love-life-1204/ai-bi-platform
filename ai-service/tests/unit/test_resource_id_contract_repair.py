from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.models import PublicationSnapshot, QueryPlan, QueryResult


@pytest.mark.parametrize("identifier", [2**53 - 1, 2**53, 2**53 + 1, 2**63 - 1])
def test_resource_ids_keep_exact_digits_at_json_boundary(identifier: int) -> None:
    plan = QueryPlan(dataset_version_id=identifier, metric_version_ids=[identifier],
                     dimension_field_ids=[identifier], limit=500)
    wire = plan.model_dump(mode="json")
    assert wire["dataset_version_id"] == str(identifier)
    assert wire["metric_version_ids"] == [str(identifier)]
    assert wire["dimension_field_ids"] == [str(identifier)]
    assert wire["limit"] == 500
    assert QueryPlan.model_validate(wire) == plan
    assert plan.dataset_version_id == identifier


@pytest.mark.parametrize("identifier", [True, 1.0, 0, -1, "01", "+1", " 1", "1 ", "1e3", 2**63])
def test_ambiguous_or_out_of_range_ids_are_rejected(identifier: object) -> None:
    with pytest.raises(ValidationError):
        QueryPlan(dataset_version_id=identifier, metric_version_ids=[1], limit=10)


def test_query_business_numbers_are_not_rewritten_as_resource_ids() -> None:
    result = QueryResult(query_id="query-exact", status="SUCCEEDED", columns=[],
                         rows=[{"id": 9007199254740993, "amount": 12.5}], row_count=1,
                         publication=PublicationSnapshot(generation_id=9007199254740993,
                                                         committed_publish_seq=2, quality_version=3),
                         execution_ms=10)
    wire = result.model_dump(mode="json")
    assert wire["publication"]["generation_id"] == "9007199254740993"
    assert wire["publication"]["committed_publish_seq"] == 2
    assert wire["rows"] == [{"id": 9007199254740993, "amount": 12.5}]
    assert wire["row_count"] == 1
    assert wire["execution_ms"] == 10
