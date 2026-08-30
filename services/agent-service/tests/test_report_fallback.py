import pytest

from app.report.fallback import ReportFallbackService


def _candidate(index: int) -> dict[str, object]:
    return {
        "product_id": f"PRODUCT-{index}",
        "sku_id": f"SKU-{index}",
        "score": f"{92 - index}.00",
        "confidence": {"score": "0.86", "level": "HIGH"},
        "price_plan": {
            "price_plan_id": f"PLAN-{index}",
            "final_price": f"{2999 + index}.00",
        },
        "facts": [{"fact_id": f"FACT-{index}", "statement": "续航能力已核验"}],
        "evidence": [{"evidence_id": f"EV-{index}", "summary": "评价证据摘要"}],
    }


def test_model_failure_builds_visible_template_without_recalculating_facts() -> None:
    candidates = [_candidate(0), _candidate(1)]

    report = ReportFallbackService().for_model_failure(candidates)

    assert report["status"] == "COMPLETED"
    assert report["generation_type"] == "TEMPLATE_FALLBACK"
    assert report["degradation_reasons"] == ["MODEL_UNAVAILABLE"]
    assert report["user_notice"] == "AI 解释暂不可用，已返回基础分析"
    assert report["recommendations"][0]["rank"] == 1
    assert report["recommendations"][0]["score"] == "92.00"
    assert report["recommendations"][0]["price_plan"]["final_price"] == "2999.00"


def test_qdrant_failure_marks_partial_and_names_missing_module() -> None:
    report = ReportFallbackService().for_rag_failure([_candidate(0)])

    assert report["status"] == "PARTIAL"
    assert report["generation_type"] == "PARTIAL_TEMPLATE_FALLBACK"
    assert report["missing_modules"] == ["RAG_EVIDENCE"]
    assert report["degradation_reasons"] == ["QDRANT_UNAVAILABLE"]
    assert report["recommendations"][0]["evidence"] == []


def test_template_fallback_refuses_to_publish_without_candidates() -> None:
    with pytest.raises(ValueError, match="确定性候选"):
        ReportFallbackService().for_model_failure([])
