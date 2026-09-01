from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from typing import Literal

from app.knowledge.retrieval import RetrievedEvidence


@dataclass(frozen=True)
class ReviewConclusion:
    topic: str
    statement: str
    sample_count: int
    evidence_ids: tuple[str, ...]


@dataclass(frozen=True)
class ReviewControversy:
    topic: str
    status: Literal["CONTROVERSIAL"]
    statement: str
    positive_sample_count: int
    negative_sample_count: int
    evidence_ids: tuple[str, ...]


@dataclass(frozen=True)
class ReviewSummary:
    advantages: list[ReviewConclusion]
    disadvantages: list[ReviewConclusion]
    controversies: list[ReviewControversy]
    sample_count: int
    average_trust_level: float
    applicable_scenarios: list[str]
    unsuitable_scenarios: list[str]


def summarize_reviews(
    evidence: Sequence[RetrievedEvidence],
    *,
    scenario_by_topic: Mapping[str, str] | None = None,
) -> ReviewSummary:
    """将已检索证据聚合为保留样本边界和冲突的结构化摘要。"""
    unique_evidence = _deduplicate(evidence)
    grouped = _group_by_topic(unique_evidence)
    scenarios = scenario_by_topic or {}
    advantages: list[ReviewConclusion] = []
    disadvantages: list[ReviewConclusion] = []
    controversies: list[ReviewControversy] = []
    applicable_scenarios: list[str] = []
    unsuitable_scenarios: list[str] = []

    for topic in sorted(grouped):
        items = grouped[topic]
        positive = [item for item in items if _sentiment(item) == "POSITIVE"]
        negative = [item for item in items if _sentiment(item) == "NEGATIVE"]

        if positive and negative:
            controversies.append(_build_controversy(topic, positive, negative))
            continue
        if positive:
            advantages.append(_build_conclusion(topic, positive))
            _append_scenario(applicable_scenarios, scenarios.get(topic))
        elif negative:
            disadvantages.append(_build_conclusion(topic, negative))
            _append_scenario(unsuitable_scenarios, scenarios.get(topic))

    trust_total = sum(_trust_level(item) for item in unique_evidence)
    average_trust = trust_total / len(unique_evidence) if unique_evidence else 0.0
    return ReviewSummary(
        advantages=advantages,
        disadvantages=disadvantages,
        controversies=controversies,
        sample_count=len(unique_evidence),
        average_trust_level=round(average_trust, 4),
        applicable_scenarios=applicable_scenarios,
        unsuitable_scenarios=unsuitable_scenarios,
    )


def _deduplicate(evidence: Sequence[RetrievedEvidence]) -> list[RetrievedEvidence]:
    best_by_id: dict[str, RetrievedEvidence] = {}
    for item in evidence:
        previous = best_by_id.get(item.evidence_id)
        if previous is None or item.final_score > previous.final_score:
            best_by_id[item.evidence_id] = item
    return [best_by_id[evidence_id] for evidence_id in sorted(best_by_id)]


def _group_by_topic(
    evidence: Sequence[RetrievedEvidence],
) -> dict[str, list[RetrievedEvidence]]:
    grouped: dict[str, list[RetrievedEvidence]] = {}
    for item in evidence:
        topic = str(item.payload.get("topic") or item.matched_topic).strip()
        if topic:
            grouped.setdefault(topic, []).append(item)
    return grouped


def _build_conclusion(topic: str, evidence: Sequence[RetrievedEvidence]) -> ReviewConclusion:
    ordered = sorted(evidence, key=lambda item: (-item.final_score, item.evidence_id))
    sample_count = len(ordered)
    prefix = "个别反馈提到" if sample_count == 1 else f"{sample_count} 条反馈提到"
    content = str(ordered[0].payload.get("content") or topic).strip()
    return ReviewConclusion(
        topic=topic,
        statement=f"{prefix}：{content}",
        sample_count=sample_count,
        evidence_ids=tuple(sorted(item.evidence_id for item in ordered)),
    )


def _build_controversy(
    topic: str,
    positive: Sequence[RetrievedEvidence],
    negative: Sequence[RetrievedEvidence],
) -> ReviewControversy:
    combined = [*positive, *negative]
    return ReviewControversy(
        topic=topic,
        status="CONTROVERSIAL",
        statement=(
            f"关于「{topic}」存在争议：{len(positive)} 条正向、{len(negative)} 条负向反馈。"
        ),
        positive_sample_count=len(positive),
        negative_sample_count=len(negative),
        evidence_ids=tuple(sorted(item.evidence_id for item in combined)),
    )


def _sentiment(evidence: RetrievedEvidence) -> str:
    return str(evidence.payload.get("sentiment") or "").strip().upper()


def _trust_level(evidence: RetrievedEvidence) -> float:
    try:
        value = float(evidence.payload.get("trust_level", 0.0))
    except (TypeError, ValueError):
        return 0.0
    return min(max(value, 0.0), 1.0)


def _append_scenario(values: list[str], value: str | None) -> None:
    normalized = (value or "").strip()
    if normalized and normalized not in values:
        values.append(normalized)
