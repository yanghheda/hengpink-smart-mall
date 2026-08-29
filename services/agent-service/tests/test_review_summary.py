from app.knowledge.retrieval import RetrievedEvidence
from app.knowledge.review_summary import summarize_reviews


def _evidence(
    evidence_id: str,
    *,
    topic: str,
    sentiment: str,
    content: str,
    trust_level: float = 0.8,
    final_score: float = 0.8,
) -> RetrievedEvidence:
    return RetrievedEvidence(
        evidence_id=evidence_id,
        matched_topic=topic,
        semantic_score=0.8,
        final_score=final_score,
        payload={
            "evidence_id": evidence_id,
            "topic": topic,
            "sentiment": sentiment,
            "content": content,
            "trust_level": trust_level,
        },
    )


def test_conflicting_evidence_is_never_summarized_as_one_sided_conclusion() -> None:
    summary = summarize_reviews(
        [
            _evidence(
                "EV-POS",
                topic="battery",
                sentiment="POSITIVE",
                content="日常使用续航稳定。",
            ),
            _evidence(
                "EV-NEG",
                topic="battery",
                sentiment="NEGATIVE",
                content="高强度使用时掉电较快。",
            ),
        ],
        scenario_by_topic={"battery": "长时间通勤"},
    )

    assert summary.sample_count == 2
    assert summary.advantages == []
    assert summary.disadvantages == []
    assert len(summary.controversies) == 1
    controversy = summary.controversies[0]
    assert controversy.topic == "battery"
    assert controversy.status == "CONTROVERSIAL"
    assert controversy.positive_sample_count == 1
    assert controversy.negative_sample_count == 1
    assert controversy.evidence_ids == ("EV-NEG", "EV-POS")
    assert "存在争议" in controversy.statement
    assert summary.applicable_scenarios == []
    assert summary.unsuitable_scenarios == []


def test_single_sample_uses_individual_feedback_wording_and_keeps_evidence_id() -> None:
    summary = summarize_reviews(
        [
            _evidence(
                "EV-ONLY",
                topic="easy_use",
                sentiment="POSITIVE",
                content="字体和入口比较清楚。",
                trust_level=0.7,
            )
        ],
        scenario_by_topic={"easy_use": "父母日常使用"},
    )

    assert summary.sample_count == 1
    assert summary.average_trust_level == 0.7
    assert len(summary.advantages) == 1
    conclusion = summary.advantages[0]
    assert conclusion.sample_count == 1
    assert conclusion.evidence_ids == ("EV-ONLY",)
    assert conclusion.statement == "个别反馈提到：字体和入口比较清楚。"
    assert summary.applicable_scenarios == ["父母日常使用"]


def test_duplicate_evidence_counts_once_and_multi_sample_wording_is_bounded() -> None:
    repeated = _evidence(
        "EV-1",
        topic="camera",
        sentiment="NEGATIVE",
        content="夜景噪点较明显。",
        final_score=0.6,
    )
    stronger_repeat = _evidence(
        "EV-1",
        topic="camera",
        sentiment="NEGATIVE",
        content="夜景细节容易丢失。",
        final_score=0.9,
    )
    summary = summarize_reviews(
        [
            repeated,
            stronger_repeat,
            _evidence(
                "EV-2",
                topic="camera",
                sentiment="NEGATIVE",
                content="暗光成片率一般。",
            ),
        ],
        scenario_by_topic={"camera": "夜景拍摄"},
    )

    assert summary.sample_count == 2
    assert len(summary.disadvantages) == 1
    conclusion = summary.disadvantages[0]
    assert conclusion.sample_count == 2
    assert conclusion.evidence_ids == ("EV-1", "EV-2")
    assert conclusion.statement == "2 条反馈提到：夜景细节容易丢失。"
    assert summary.unsuitable_scenarios == ["夜景拍摄"]


def test_neutral_or_missing_sentiment_is_counted_but_does_not_create_a_conclusion() -> None:
    summary = summarize_reviews(
        [
            _evidence(
                "EV-NEUTRAL",
                topic="after_sales",
                sentiment="NEUTRAL",
                content="保修条款以页面说明为准。",
            )
        ]
    )

    assert summary.sample_count == 1
    assert summary.advantages == []
    assert summary.disadvantages == []
    assert summary.controversies == []
    assert summary.applicable_scenarios == []
    assert summary.unsuitable_scenarios == []
