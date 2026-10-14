from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.schemas.models import SemanticCatalog, SemanticMetric


@dataclass(frozen=True, slots=True)
class RankedMetric:
    metric_version_id: int
    score: float
    matched_by: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class RetrievalDecision:
    metrics: tuple[RankedMetric, ...]
    analysis_shape: str


def retrieve_metrics(question: str, catalog: SemanticCatalog, top_k: int = 5) -> RetrievalDecision:
    """Rank only the already-authorized catalog; this function is not an auth boundary."""
    normalized = _normalize(question)
    question_tokens = _tokens(normalized)
    ranked: list[RankedMetric] = []
    for metric in catalog.metrics:
        score, matched = _score(metric, normalized, question_tokens)
        if score > 0.5:
            ranked.append(RankedMetric(metric.metric_version_id, round(score, 6), tuple(matched)))
    ranked.sort(key=lambda item: (-item.score, item.metric_version_id))
    if not ranked and len(catalog.metrics) == 1:
        only = catalog.metrics[0]
        ranked.append(RankedMetric(only.metric_version_id, 0.51, ("single_authorized_metric",)))
    return RetrievalDecision(tuple(ranked[:top_k]), _analysis_shape(normalized))


def _score(metric: SemanticMetric, question: str, question_tokens: set[str]) -> tuple[float, list[str]]:
    candidates = [("name", metric.name), ("code", metric.code)]
    candidates.extend(("alias", alias) for alias in metric.aliases)
    score = 0.0
    matched: list[str] = []
    for kind, value in candidates:
        normalized = _normalize(value)
        if not normalized:
            continue
        if normalized in question:
            weight = {"name": 10.0, "alias": 8.0, "code": 7.0}[kind]
            if weight > score:
                score = weight
            matched.append(f"exact_{kind}")
        candidate_tokens = _tokens(normalized)
        union = question_tokens | candidate_tokens
        overlap = len(question_tokens & candidate_tokens) / len(union) if union else 0.0
        trigram = _jaccard(_ngrams(question, 3), _ngrams(normalized, 3))
        fuzzy = overlap * 4 + trigram * 3
        if fuzzy > score:
            score = fuzzy
            if fuzzy > 0.5:
                matched.append(f"fuzzy_{kind}")
    return score, list(dict.fromkeys(matched))


def _analysis_shape(question: str) -> str:
    if any(word in question for word in ("预测", "未来", "forecast")):
        return "FORECAST"
    if any(word in question for word in ("异常", "突增", "突降", "波动")):
        return "ANOMALY"
    if any(word in question for word in ("相关", "关系", "影响")):
        return "CORRELATE"
    if any(word in question for word in ("对比", "比较", "与", "和", "环比", "同比")):
        return "COMPARE"
    if any(word in question for word in ("趋势", "走势", "变化")):
        return "TREND"
    return "LOOKUP"


def _normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).lower().strip()
    return re.sub(r"[^\w\u4e00-\u9fff]+", "", value)


def _tokens(value: str) -> set[str]:
    latin = set(re.findall(r"[a-z0-9_]+", value))
    chinese = "".join(re.findall(r"[\u4e00-\u9fff]", value))
    return latin | _ngrams(chinese, 2) | ({chinese} if chinese else set())


def _ngrams(value: str, size: int) -> set[str]:
    if not value:
        return set()
    if len(value) <= size:
        return {value}
    return {value[index:index + size] for index in range(len(value) - size + 1)}


def _jaccard(left: set[str], right: set[str]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 0.0
