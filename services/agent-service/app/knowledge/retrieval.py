from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any

from app.intent.models import StructuredIntent
from app.knowledge.security import PROMPT_INJECTION_WARNING

PER_TOPIC_TOP_K = 8
MIN_TOPIC_COUNT = 2
MAX_TOPIC_COUNT = 4
MAX_EVIDENCE_PER_SKU = 10


@dataclass(frozen=True)
class TopicQuery:
    topic: str
    text: str


@dataclass(frozen=True)
class RetrievalTarget:
    dataset_version: str
    category_id: str
    product_id: str
    sku_id: str


@dataclass(frozen=True)
class RetrievedEvidence:
    evidence_id: str
    matched_topic: str
    semantic_score: float
    final_score: float
    payload: dict[str, Any]


@dataclass
class RetrievalTrace:
    warning_codes: list[str] = field(default_factory=list)
    excluded_evidence_ids: list[str] = field(default_factory=list)


def build_qdrant_filter(target: RetrievalTarget) -> dict[str, Any]:
    """构造服务端 Payload 过滤，同时保留 Product 级通用证据。"""
    return {
        "must": [
            {"key": "dataset_version", "match": {"value": target.dataset_version}},
            {"key": "category_id", "match": {"value": target.category_id}},
            {"key": "product_id", "match": {"value": target.product_id}},
            {
                "should": [
                    {"key": "sku_id", "match": {"value": target.sku_id}},
                    {"is_null": {"key": "sku_id"}},
                ]
            },
        ]
    }


def generate_topic_queries(intent: StructuredIntent, product_name: str) -> list[TopicQuery]:
    """从结构化意图生成确定性的主题查询，不让模型补造业务事实。"""
    ordered_topics: list[str] = []
    preferences = sorted(
        intent.preferences,
        key=lambda item: (-item.weight, item.name),
    )
    for preference in preferences:
        _append_unique(ordered_topics, preference.name)
    for scenario in intent.usage_scenarios:
        _append_unique(ordered_topics, scenario)
    for fallback_topic in ("common_risk", "after_sales"):
        if len(ordered_topics) >= MIN_TOPIC_COUNT:
            break
        _append_unique(ordered_topics, fallback_topic)

    context = " ".join(part for part in (intent.recipient, *intent.usage_scenarios[:2]) if part)
    return [
        TopicQuery(
            topic=topic,
            text=" ".join(
                part for part in (product_name, context, topic, "优点 缺点 风险") if part
            ),
        )
        for topic in ordered_topics[:MAX_TOPIC_COUNT]
    ]


def retrieve_evidence(
    *,
    points_by_topic: dict[str, list[dict[str, Any]]],
    topics: list[str],
    target: RetrievalTarget,
    now: datetime | None = None,
    trace: RetrievalTrace | None = None,
) -> list[RetrievedEvidence]:
    """对各主题召回结果做强过滤、去重和确定性重排。"""
    current_time = now or datetime.now(UTC)
    best_by_evidence_id: dict[str, RetrievedEvidence] = {}

    for topic in topics[:MAX_TOPIC_COUNT]:
        topic_points = points_by_topic.get(topic, [])
        _record_injection_exclusions(topic_points, target, trace)
        eligible = [
            point
            for point in topic_points
            if _matches_target(point.get("payload", {}), target)
            and not bool(point.get("payload", {}).get("injection_flag", False))
        ]
        eligible.sort(key=lambda point: float(point.get("score", 0.0)), reverse=True)
        for point in eligible[:PER_TOPIC_TOP_K]:
            payload = dict(point["payload"])
            semantic_score = float(point.get("score", 0.0))
            evidence = RetrievedEvidence(
                evidence_id=str(payload["evidence_id"]),
                matched_topic=topic,
                semantic_score=semantic_score,
                final_score=_rerank_score(payload, topic, semantic_score, current_time),
                payload=payload,
            )
            previous = best_by_evidence_id.get(evidence.evidence_id)
            if previous is None or evidence.final_score > previous.final_score:
                best_by_evidence_id[evidence.evidence_id] = evidence

    ranked = sorted(
        best_by_evidence_id.values(),
        key=lambda item: (-item.final_score, item.evidence_id),
    )
    return ranked[:MAX_EVIDENCE_PER_SKU]


def _record_injection_exclusions(
    points: list[dict[str, Any]],
    target: RetrievalTarget,
    trace: RetrievalTrace | None,
) -> None:
    if trace is None:
        return
    excluded = sorted(
        {
            str(point.get("payload", {}).get("evidence_id", ""))
            for point in points
            if _matches_target(point.get("payload", {}), target)
            and bool(point.get("payload", {}).get("injection_flag", False))
            and point.get("payload", {}).get("evidence_id")
        }
    )
    for evidence_id in excluded:
        if evidence_id not in trace.excluded_evidence_ids:
            trace.excluded_evidence_ids.append(evidence_id)
    if excluded and PROMPT_INJECTION_WARNING not in trace.warning_codes:
        trace.warning_codes.append(PROMPT_INJECTION_WARNING)


def _append_unique(values: list[str], value: str) -> None:
    normalized = value.strip()
    if normalized and normalized not in values:
        values.append(normalized)


def _matches_target(payload: dict[str, Any], target: RetrievalTarget) -> bool:
    if payload.get("dataset_version") != target.dataset_version:
        return False
    if payload.get("category_id") != target.category_id:
        return False
    if payload.get("product_id") != target.product_id:
        return False
    # Product 级证据可被同 Product 的 SKU 复用；SKU 级证据只能进入自身。
    return payload.get("sku_id") in (None, "", target.sku_id)


def _rerank_score(
    payload: dict[str, Any], topic: str, semantic_score: float, now: datetime
) -> float:
    trust_level = float(Decimal(str(payload.get("trust_level", 0))))
    topic_match = 1.0 if payload.get("topic") == topic else 0.0
    freshness = _freshness_score(payload.get("published_at"), now)
    return semantic_score * 0.65 + trust_level * 0.15 + topic_match * 0.15 + freshness * 0.05


def _freshness_score(published_at: Any, now: datetime) -> float:
    if not isinstance(published_at, str):
        return 0.0
    try:
        published = datetime.fromisoformat(published_at)
    except ValueError:
        return 0.0
    if published.tzinfo is None:
        published = published.replace(tzinfo=UTC)
    age_days = max((now.astimezone(UTC) - published.astimezone(UTC)).days, 0)
    return max(0.0, 1.0 - age_days / 365)
