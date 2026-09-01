from copy import deepcopy

import pytest
from pydantic import ValidationError

from app.report.models import DecisionReportNarrative
from app.report.prompt import load_decision_report_prompt
from app.report.service import DecisionReportComposer


def _candidate(index: int) -> dict[str, object]:
    sku_id = f"SKU-{index}"
    return {
        "product_id": f"PRODUCT-{index}",
        "sku_id": sku_id,
        "score": str(92 - index),
        "confidence": {"score": "0.86", "level": "HIGH"},
        "price_plan": {
            "price_plan_id": f"PRICE-PLAN-{index}",
            "final_price": f"{2999 + index}.00",
            "conditions": ["无需会员"],
        },
        "facts": [{"fact_id": f"FACT-{index}", "statement": "续航能力已核验"}],
        "evidence": [{"evidence_id": f"EV-{index}", "summary": "评价证据摘要", "topic": "battery"}],
    }


def _narrative() -> dict[str, object]:
    return {
        "summary": "首选更贴合长辈日常使用，备选各有取舍。",
        "recommendations": [
            {
                "candidate_slot": "PRIMARY",
                "reasons": [
                    {
                        "text": "续航更适合日常使用。",
                        "fact_ids": ["FACT-0"],
                        "evidence_ids": ["EV-0"],
                    }
                ],
                "risks": ["机身尺寸需要到店确认。"],
                "data_gaps": ["缺少长期耐用性样本。"],
            },
            {
                "candidate_slot": "ALTERNATIVE_1",
                "reasons": [
                    {
                        "text": "作为备选也具备稳定续航。",
                        "fact_ids": ["FACT-1"],
                        "evidence_ids": ["EV-1"],
                    }
                ],
                "risks": [],
                "data_gaps": [],
            },
        ],
        "rejected_popular_candidates": [
            {"label": "热门候选 A", "reason": "未满足已声明的硬条件。"}
        ],
        "counterfactuals": ["若更重视拍照，备选排序可能变化。"],
        "overall_data_gaps": ["部分评价样本较少。"],
    }


@pytest.mark.parametrize(
    "field,value",
    [("score", "100"), ("final_price", "0.01"), ("rank", 9), ("sku_id", "FAKE")],
)
def test_narrative_schema_rejects_model_attempt_to_write_deterministic_facts(
    field: str, value: object
) -> None:
    payload = _narrative()
    recommendations = payload["recommendations"]
    assert isinstance(recommendations, list)
    first_recommendation = recommendations[0]
    assert isinstance(first_recommendation, dict)
    first_recommendation[field] = value

    with pytest.raises(ValidationError):
        DecisionReportNarrative.model_validate(payload)


def test_prompt_input_only_contains_top_three_candidates_and_does_not_mutate_source() -> None:
    candidates = [_candidate(index) for index in range(5)]
    snapshot = deepcopy(candidates)

    prompt_input = DecisionReportComposer().build_prompt_input(
        intent_summary={"recipient": "父母", "usage_scenarios": ["日常使用"]},
        ranked_candidates=candidates,
        rejected_popular_candidates=[],
    )

    assert [item["slot"] for item in prompt_input["candidates"]] == [
        "PRIMARY",
        "ALTERNATIVE_1",
        "ALTERNATIVE_2",
    ]
    assert len(prompt_input["candidates"]) == 3
    assert candidates == snapshot


def test_composer_projects_ids_score_price_and_rank_from_deterministic_input() -> None:
    candidates = [_candidate(index) for index in range(3)]
    composer = DecisionReportComposer()
    prompt_input = composer.build_prompt_input(
        intent_summary={"recipient": "父母"},
        ranked_candidates=candidates,
        rejected_popular_candidates=[],
    )

    report = composer.compose(prompt_input, _narrative())

    primary = report["recommendations"][0]
    alternative = report["recommendations"][1]
    assert primary["rank"] == 1
    assert primary["product_id"] == "PRODUCT-0"
    assert primary["sku_id"] == "SKU-0"
    assert primary["score"] == "92"
    assert primary["price_plan"]["final_price"] == "2999.00"
    assert alternative["rank"] == 2
    assert alternative["sku_id"] == "SKU-1"


def test_prompt_declares_language_only_boundary_and_machine_readable_schema() -> None:
    prompt = load_decision_report_prompt()

    assert prompt.version == "decision-report-v1"
    assert "不得生成或修改金额、最终评分、排名" in prompt.content
    assert "output_schema" in prompt.content
    item_reference = prompt.output_schema["properties"]["recommendations"]["items"]["$ref"]
    item_schema_name = item_reference.rsplit("/", maxsplit=1)[-1]
    assert "candidate_slot" in prompt.output_schema["$defs"][item_schema_name]["properties"]
