from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from app.retrieval.semantic_ranker import retrieve_metrics
from app.schemas.models import SemanticCatalog


@dataclass(frozen=True, slots=True)
class EvaluationReport:
    case_count: int
    hit_at_3: float
    mean_reciprocal_rank: float
    intent_accuracy: float
    complex_exact_match: float
    passed: bool

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def evaluate_fixture(path: str | Path) -> EvaluationReport:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    catalog = SemanticCatalog.model_validate(payload["catalog"])
    cases = payload["cases"]
    hits = 0
    reciprocal_rank = 0.0
    intent_hits = 0
    complex_cases = 0
    complex_hits = 0
    for case in cases:
        decision = retrieve_metrics(case["question"], catalog, top_k=3)
        actual = [item.metric_version_id for item in decision.metrics]
        expected = list(case["expected_metric_version_ids"])
        positions = [actual.index(metric_id) + 1 for metric_id in expected if metric_id in actual]
        hits += int(bool(positions))
        reciprocal_rank += 1 / min(positions) if positions else 0
        intent_hits += int(decision.analysis_shape == case["expected_shape"])
        if len(expected) > 1:
            complex_cases += 1
            complex_hits += int(set(actual[:len(expected)]) == set(expected))
    total = len(cases)
    hit_at_3 = hits / total if total else 0.0
    mrr = reciprocal_rank / total if total else 0.0
    intent_accuracy = intent_hits / total if total else 0.0
    complex_exact = complex_hits / complex_cases if complex_cases else 1.0
    thresholds = payload.get("thresholds", {})
    passed = (
        hit_at_3 >= thresholds.get("hit_at_3", 0.8)
        and mrr >= thresholds.get("mean_reciprocal_rank", 0.75)
        and intent_accuracy >= thresholds.get("intent_accuracy", 0.8)
        and complex_exact >= thresholds.get("complex_exact_match", 0.7)
    )
    return EvaluationReport(total, hit_at_3, mrr, intent_accuracy, complex_exact, passed)
