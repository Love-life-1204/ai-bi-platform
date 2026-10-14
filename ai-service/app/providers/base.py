from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from app.schemas.models import Answer, Fact


class LanguageProvider(ABC):
    @abstractmethod
    def classify_intent(self, question: str) -> tuple[str, float]:
        raise NotImplementedError

    @abstractmethod
    def write_answer(
        self,
        question: str,
        facts: list[Fact],
        assumptions: list[str],
        limitations: list[str],
    ) -> Answer:
        raise NotImplementedError


class RuleBasedLanguageProvider(LanguageProvider):
    """Safe local baseline used when no external model is approved for the tenant."""

    def classify_intent(self, question: str) -> tuple[str, float]:
        normalized = question.lower()
        if any(word in normalized for word in ("帮助", "怎么用", "help")):
            return "HELP", 0.95
        if any(word in normalized for word in ("定义", "口径", "什么意思", "什么是")):
            return "EXPLAIN", 0.85
        return "ANALYZE", 0.8

    def write_answer(
        self,
        question: str,
        facts: list[Fact],
        assumptions: list[str],
        limitations: list[str],
    ) -> Answer:
        if not facts:
            summary = "当前授权数据不足以形成可靠结论。"
        elif len(facts) == 1:
            fact = facts[0]
            suffix = f" {fact.unit}" if fact.unit else ""
            summary = f"{fact.statement}：{fact.value}{suffix}。"
        else:
            summary = f"已基于受控查询得到 {len(facts)} 条可核验事实。"
        recommendations = []
        if facts:
            recommendations.append("如需进一步判断原因，可在相同口径下增加时间或业务维度进行对比。")
        return Answer(
            summary=summary,
            facts=facts,
            assumptions=assumptions,
            recommendations=recommendations,
            limitations=limitations,
        )


class FailingLanguageProvider(LanguageProvider):
    def classify_intent(self, question: str) -> tuple[str, float]:
        raise RuntimeError("provider unavailable")

    def write_answer(self, question: str, facts: list[Fact], assumptions: list[str], limitations: list[str]) -> Answer:
        raise RuntimeError("provider unavailable")

