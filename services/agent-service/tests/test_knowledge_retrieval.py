from datetime import UTC, datetime

from app.intent.models import IntentCategory, IntentPreference, IntentSource, StructuredIntent
from app.knowledge.retrieval import (
    RetrievalTarget,
    build_qdrant_filter,
    generate_topic_queries,
    retrieve_evidence,
)


def _point(
    evidence_id: str,
    *,
    sku_id: str | None,
    score: float,
    dataset_version: str = "commerce-demo-2026.08.1",
    product_id: str = "P-1",
    category_id: str = "PHONE",
    topic: str = "battery",
) -> dict:
    return {
        "score": score,
        "payload": {
            "evidence_id": evidence_id,
            "product_id": product_id,
            "sku_id": sku_id,
            "category_id": category_id,
            "dataset_version": dataset_version,
            "source_type": "SIMULATED_REVIEW",
            "topic": topic,
            "sentiment": "POSITIVE",
            "trust_level": 0.8,
            "published_at": "2026-08-01T00:00:00Z",
            "content": "续航表现稳定。",
            "is_simulated": True,
        },
    }


def test_topic_queries_are_bounded_and_come_from_explicit_intent() -> None:
    intent = StructuredIntent(
        category=IntentCategory.PHONE,
        recipient="父母",
        usage_scenarios=["日常通勤", "家庭拍照"],
        preferences=[
            IntentPreference(name="battery", weight="0.80", source=IntentSource.USER_EXPLICIT),
            IntentPreference(name="easy_use", weight="0.70", source=IntentSource.USER_EXPLICIT),
            IntentPreference(name="camera", weight="0.60", source=IntentSource.USER_EXPLICIT),
        ],
    )

    queries = generate_topic_queries(intent, product_name="Northstar N9A")

    assert 2 <= len(queries) <= 4
    assert [query.topic for query in queries[:3]] == ["battery", "easy_use", "camera"]
    assert all("Northstar N9A" in query.text and "父母" in query.text for query in queries)


def test_strong_filter_never_returns_sibling_sku_or_wrong_scope() -> None:
    target = RetrievalTarget(
        dataset_version="commerce-demo-2026.08.1",
        category_id="PHONE",
        product_id="P-1",
        sku_id="S-1",
    )
    points = [
        _point("EV-SIBLING", sku_id="S-2", score=1.0),
        _point("EV-WRONG-DATASET", sku_id="S-1", score=0.99, dataset_version="old"),
        _point("EV-WRONG-CATEGORY", sku_id="S-1", score=0.98, category_id="MONITOR"),
        _point("EV-WRONG-PRODUCT", sku_id="S-1", score=0.97, product_id="P-2"),
        _point("EV-SKU", sku_id="S-1", score=0.70),
        _point("EV-PRODUCT", sku_id=None, score=0.60),
    ]

    result = retrieve_evidence(
        points_by_topic={"battery": points},
        topics=["battery"],
        target=target,
        now=datetime(2026, 8, 30, tzinfo=UTC),
    )

    assert [item.evidence_id for item in result] == ["EV-SKU", "EV-PRODUCT"]
    assert all(item.payload["sku_id"] in {None, "S-1"} for item in result)


def test_qdrant_filter_pushes_all_scope_constraints_to_vector_search() -> None:
    target = RetrievalTarget(
        dataset_version="commerce-demo-2026.08.1",
        category_id="PHONE",
        product_id="P-1",
        sku_id="S-1",
    )

    assert build_qdrant_filter(target) == {
        "must": [
            {"key": "dataset_version", "match": {"value": "commerce-demo-2026.08.1"}},
            {"key": "category_id", "match": {"value": "PHONE"}},
            {"key": "product_id", "match": {"value": "P-1"}},
            {
                "should": [
                    {"key": "sku_id", "match": {"value": "S-1"}},
                    {"is_null": {"key": "sku_id"}},
                ]
            },
        ]
    }


def test_duplicate_evidence_keeps_best_score_and_remains_locatable() -> None:
    target = RetrievalTarget(
        dataset_version="commerce-demo-2026.08.1",
        category_id="PHONE",
        product_id="P-1",
        sku_id="S-1",
    )
    duplicate_low = _point("EV-1", sku_id="S-1", score=0.30, topic="battery")
    duplicate_high = _point("EV-1", sku_id="S-1", score=0.90, topic="easy_use")

    result = retrieve_evidence(
        points_by_topic={"battery": [duplicate_low], "easy_use": [duplicate_high]},
        topics=["battery", "easy_use"],
        target=target,
        now=datetime(2026, 8, 30, tzinfo=UTC),
    )

    assert len(result) == 1
    assert result[0].evidence_id == "EV-1"
    assert result[0].semantic_score == 0.90
    assert result[0].matched_topic == "easy_use"
