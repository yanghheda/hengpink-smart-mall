import json
from pathlib import Path
from typing import Any

import pytest
from pydantic import ValidationError

from evals.golden_runner import GoldenCase, evaluate_cases, load_cases


def _case(**overrides: object) -> dict[str, object]:
    case: dict[str, object] = {
        "caseId": "GC-PHONE-001",
        "versions": {
            "datasetVersion": "commerce-demo-2026.08.1",
            "promptVersion": "intent-v1",
            "pricingRuleVersion": "pricing-v1",
            "scoringVersion": "scoring-v1",
        },
        "input": "给爸妈买 3000 元以内手机",
        "expected": {
            "intent": {"category": "PHONE", "budgetMax": "3000.00"},
            "product": {
                "relevantProductIds": ["P-1", "P-2"],
                "forbiddenProductIds": ["P-X"],
            },
            "pricing": {"finalPrice": "2699.00", "promotionIds": ["PROMO-1"]},
            "citation": {"requiredReasonIds": ["R-1", "R-2"]},
        },
        "actual": {
            "datasetVersion": "commerce-demo-2026.08.1",
            "intent": {"category": "PHONE", "budgetMax": "3000.00"},
            "product": {"topProductIds": ["P-1", "P-3", "P-2"]},
            "pricing": {"finalPrice": "2699.00", "promotionIds": ["PROMO-1"]},
            "citation": {
                "reasons": [
                    {"reasonId": "R-1", "evidenceIds": ["EV-1"], "factIds": []},
                    {"reasonId": "R-2", "evidenceIds": [], "factIds": ["FACT-2"]},
                ]
            },
        },
    }
    case.update(overrides)
    return case


def test_report_contains_versions_denominators_and_no_failures() -> None:
    report = evaluate_cases([GoldenCase.model_validate(_case())])

    assert report.case_count == 1
    assert report.versions.dataset_versions == ["commerce-demo-2026.08.1"]
    assert report.metrics["intent_field_accuracy"].model_dump() == {
        "numerator": 2,
        "denominator": 2,
        "value": "1.0000",
    }
    assert report.metrics["product_recall_at_10"].denominator == 2
    assert report.metrics["pricing_accuracy"].denominator == 1
    assert report.metrics["citation_completeness"].denominator == 2
    assert report.failures == []


def test_failures_keep_case_metric_and_expected_actual_details() -> None:
    raw: Any = _case()
    raw["actual"]["product"]["topProductIds"] = ["P-1", "P-2", "P-X"]
    raw["actual"]["pricing"]["finalPrice"] = "2700.00"
    raw["actual"]["citation"]["reasons"][1] = {
        "reasonId": "R-2",
        "evidenceIds": [],
        "factIds": [],
    }

    report = evaluate_cases([GoldenCase.model_validate(raw)])

    assert report.metrics["hard_constraint_violation_rate"].numerator == 1
    assert {failure.metric for failure in report.failures} == {
        "hard_constraint_violation_rate",
        "pricing_accuracy",
        "citation_completeness",
    }
    pricing_failure = next(
        item for item in report.failures if item.metric == "pricing_accuracy"
    )
    assert pricing_failure.case_id == "GC-PHONE-001"
    assert pricing_failure.expected == "2699.00|PROMO-1"
    assert pricing_failure.actual == "2700.00|PROMO-1"


def test_zero_denominator_is_explicitly_not_calculable() -> None:
    raw = _case()
    raw["expected"] = {}
    raw["actual"] = {"datasetVersion": "commerce-demo-2026.08.1"}

    report = evaluate_cases([GoldenCase.model_validate(raw)])

    metric = report.metrics["pricing_accuracy"]
    assert metric.denominator == 0
    assert metric.value is None


def test_dataset_version_mismatch_is_a_case_failure_not_a_silent_comparison() -> None:
    raw: Any = _case()
    raw["actual"]["datasetVersion"] = "commerce-demo-2026.08.2"

    report = evaluate_cases([GoldenCase.model_validate(raw)])

    assert len(report.failures) == 1
    assert report.failures[0].metric == "dataset_version_match"
    assert all(metric.denominator == 0 for metric in report.metrics.values())


def test_loader_rejects_duplicate_ids_and_non_decimal_money(tmp_path: Path) -> None:
    invalid_money: Any = _case()
    invalid_money["expected"]["pricing"]["finalPrice"] = 2699.0
    invalid_path = tmp_path / "invalid.json"
    invalid_path.write_text(json.dumps([invalid_money]), encoding="utf-8")

    with pytest.raises(ValidationError):
        load_cases(invalid_path)

    duplicate_path = tmp_path / "duplicate.json"
    duplicate_path.write_text(json.dumps([_case(), _case()]), encoding="utf-8")

    with pytest.raises(ValueError, match="Case ID 重复"):
        load_cases(duplicate_path)
