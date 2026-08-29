from app.knowledge.citation import CitationTarget, DraftReason, validate_citations
from app.knowledge.retrieval import RetrievedEvidence


def _evidence(
    evidence_id: str,
    *,
    dataset_version: str = "commerce-demo-2026.08.1",
    product_id: str = "P-1",
    sku_id: str | None = "S-1",
    topic: str = "battery",
    injection_flag: bool = False,
) -> RetrievedEvidence:
    return RetrievedEvidence(
        evidence_id=evidence_id,
        matched_topic=topic,
        semantic_score=0.8,
        final_score=0.8,
        payload={
            "evidence_id": evidence_id,
            "dataset_version": dataset_version,
            "product_id": product_id,
            "sku_id": sku_id,
            "category_id": "PHONE",
            "topic": topic,
            "injection_flag": injection_flag,
        },
    )


def _target() -> CitationTarget:
    return CitationTarget(
        dataset_version="commerce-demo-2026.08.1",
        product_id="P-1",
        sku_id="S-1",
    )


def test_validator_keeps_only_passed_and_owned_evidence() -> None:
    reasons = [
        DraftReason(
            reason_id="R-1",
            topic="battery",
            statement="续航表现稳定。",
            evidence_ids=("EV-GOOD", "EV-SIBLING", "EV-FABRICATED"),
        )
    ]
    available = [
        _evidence("EV-GOOD"),
        _evidence("EV-SIBLING", sku_id="S-2"),
    ]

    result = validate_citations(reasons, available_evidence=available, target=_target())

    assert len(result.reasons) == 1
    assert result.reasons[0].evidence_ids == ("EV-GOOD",)
    assert [(issue.evidence_id, issue.code) for issue in result.issues] == [
        ("EV-SIBLING", "CITATION_SKU_MISMATCH"),
        ("EV-FABRICATED", "CITATION_NOT_IN_MODEL_CONTEXT"),
    ]


def test_invalid_topic_version_and_injection_remove_unsupported_reason() -> None:
    reasons = [
        DraftReason(
            reason_id="R-2",
            topic="battery",
            statement="没有合法依据的结论。",
            evidence_ids=("EV-TOPIC", "EV-VERSION", "EV-INJECT"),
        )
    ]
    available = [
        _evidence("EV-TOPIC", topic="camera"),
        _evidence("EV-VERSION", dataset_version="old"),
        _evidence("EV-INJECT", injection_flag=True),
    ]

    result = validate_citations(reasons, available_evidence=available, target=_target())

    assert result.reasons == []
    assert result.removed_reason_ids == ["R-2"]
    assert {issue.code for issue in result.issues} == {
        "CITATION_TOPIC_MISMATCH",
        "CITATION_DATASET_MISMATCH",
        "CITATION_INJECTION_BLOCKED",
    }


def test_fact_backed_reason_survives_after_all_invalid_citations_are_removed() -> None:
    reason = DraftReason(
        reason_id="R-FACT",
        topic="battery",
        statement="电池容量来自结构化规格。",
        evidence_ids=("EV-NOT-PASSED",),
        fact_ids=("FACT-BATTERY-MAH",),
    )

    result = validate_citations([reason], available_evidence=[], target=_target())

    assert len(result.reasons) == 1
    assert result.reasons[0].evidence_ids == ()
    assert result.reasons[0].fact_ids == ("FACT-BATTERY-MAH",)
    assert result.removed_reason_ids == []
