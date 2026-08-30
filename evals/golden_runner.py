from __future__ import annotations

import argparse
import json
from collections.abc import Iterable
from decimal import Decimal, InvalidOperation
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda name: _to_camel(name),
        populate_by_name=True,
        extra="forbid",
    )


def _to_camel(name: str) -> str:
    head, *tail = name.split("_")
    return head + "".join(part.title() for part in tail)


def _money(value: str) -> str:
    """金额只接受两位小数字符串，评测器不参与业务金额计算。"""
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError("金额不是合法十进制数") from exc
    if not parsed.is_finite() or parsed < 0 or parsed.as_tuple().exponent != -2:
        raise ValueError("金额必须是非负两位小数字符串")
    return value


class CaseVersions(StrictModel):
    dataset_version: str = Field(min_length=1)
    prompt_version: str = Field(min_length=1)
    pricing_rule_version: str = Field(min_length=1)
    scoring_version: str = Field(min_length=1)


class IntentSnapshot(StrictModel):
    category: str | None = None
    recipient: str | None = None
    budget_max: str | None = None
    hard_constraints: list[str] | None = None

    @field_validator("budget_max")
    @classmethod
    def validate_budget(cls, value: str | None) -> str | None:
        return None if value is None else _money(value)


class ExpectedProduct(StrictModel):
    relevant_product_ids: list[str] = Field(default_factory=list)
    forbidden_product_ids: list[str] = Field(default_factory=list)


class ActualProduct(StrictModel):
    top_product_ids: list[str] = Field(default_factory=list, max_length=10)


class PricingSnapshot(StrictModel):
    final_price: str
    promotion_ids: list[str] = Field(default_factory=list)

    @field_validator("final_price")
    @classmethod
    def validate_final_price(cls, value: str) -> str:
        return _money(value)


class ExpectedCitation(StrictModel):
    required_reason_ids: list[str] = Field(default_factory=list)


class CitationReason(StrictModel):
    reason_id: str
    evidence_ids: list[str] = Field(default_factory=list)
    fact_ids: list[str] = Field(default_factory=list)


class ActualCitation(StrictModel):
    reasons: list[CitationReason] = Field(default_factory=list)


class ExpectedSnapshot(StrictModel):
    intent: IntentSnapshot | None = None
    product: ExpectedProduct | None = None
    pricing: PricingSnapshot | None = None
    citation: ExpectedCitation | None = None


class ActualSnapshot(StrictModel):
    dataset_version: str
    intent: IntentSnapshot | None = None
    product: ActualProduct | None = None
    pricing: PricingSnapshot | None = None
    citation: ActualCitation | None = None


class GoldenCase(StrictModel):
    case_id: str = Field(min_length=1)
    versions: CaseVersions
    input: str = Field(min_length=1)
    expected: ExpectedSnapshot
    actual: ActualSnapshot


class MetricResult(StrictModel):
    numerator: int = Field(ge=0)
    denominator: int = Field(ge=0)
    value: str | None


class FailureSample(StrictModel):
    case_id: str
    metric: str
    expected: str
    actual: str


class ReportVersions(StrictModel):
    dataset_versions: list[str]
    prompt_versions: list[str]
    pricing_rule_versions: list[str]
    scoring_versions: list[str]


class GoldenReport(StrictModel):
    case_count: int
    versions: ReportVersions
    metrics: dict[str, MetricResult]
    failures: list[FailureSample]


class _Counter:
    def __init__(self) -> None:
        self.numerator = 0
        self.denominator = 0

    def add(self, passed: bool) -> None:
        self.denominator += 1
        self.numerator += int(passed)

    def result(self) -> MetricResult:
        value = None
        if self.denominator:
            value = f"{Decimal(self.numerator) / Decimal(self.denominator):.4f}"
        return MetricResult(
            numerator=self.numerator, denominator=self.denominator, value=value
        )


def load_cases(path: Path) -> list[GoldenCase]:
    """读取并严格校验 Case 文件，同时拒绝重复标识。"""
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise TypeError("Golden Case 文件顶层必须是数组")
    cases = [GoldenCase.model_validate(item) for item in raw]
    case_ids = [case.case_id for case in cases]
    if len(case_ids) != len(set(case_ids)):
        raise ValueError("Case ID 重复")
    return cases


def evaluate_cases(cases: Iterable[GoldenCase]) -> GoldenReport:
    """计算 P14-S02 范围内的确定性指标。"""
    case_list = list(cases)
    counters = {
        "intent_field_accuracy": _Counter(),
        "product_recall_at_10": _Counter(),
        "hard_constraint_violation_rate": _Counter(),
        "pricing_accuracy": _Counter(),
        "citation_completeness": _Counter(),
    }
    failures: list[FailureSample] = []

    for case in case_list:
        if case.versions.dataset_version != case.actual.dataset_version:
            failures.append(
                FailureSample(
                    case_id=case.case_id,
                    metric="dataset_version_match",
                    expected=case.versions.dataset_version,
                    actual=case.actual.dataset_version,
                )
            )
            continue
        _evaluate_intent(case, counters["intent_field_accuracy"], failures)
        _evaluate_product(
            case,
            counters["product_recall_at_10"],
            counters["hard_constraint_violation_rate"],
            failures,
        )
        _evaluate_pricing(case, counters["pricing_accuracy"], failures)
        _evaluate_citations(case, counters["citation_completeness"], failures)

    return GoldenReport(
        case_count=len(case_list),
        versions=_collect_versions(case_list),
        metrics={name: counter.result() for name, counter in counters.items()},
        failures=failures,
    )


def _evaluate_intent(
    case: GoldenCase, counter: _Counter, failures: list[FailureSample]
) -> None:
    expected = case.expected.intent
    if expected is None:
        return
    actual = case.actual.intent
    expected_fields = expected.model_dump(exclude_none=True)
    actual_fields = actual.model_dump(exclude_none=True) if actual else {}
    for field, expected_value in expected_fields.items():
        actual_value = actual_fields.get(field)
        passed = actual_value == expected_value
        counter.add(passed)
        if not passed:
            failures.append(
                _failure(case, f"intent.{field}", expected_value, actual_value)
            )


def _evaluate_product(
    case: GoldenCase,
    recall: _Counter,
    violations: _Counter,
    failures: list[FailureSample],
) -> None:
    expected = case.expected.product
    if expected is None:
        return
    actual_ids = case.actual.product.top_product_ids if case.actual.product else []
    for product_id in expected.relevant_product_ids:
        passed = product_id in actual_ids
        recall.add(passed)
        if not passed:
            failures.append(
                _failure(case, "product_recall_at_10", product_id, actual_ids)
            )
    for product_id in expected.forbidden_product_ids:
        violated = product_id in actual_ids
        violations.add(violated)
        if violated:
            failures.append(
                _failure(case, "hard_constraint_violation_rate", "不应出现", product_id)
            )


def _evaluate_pricing(
    case: GoldenCase, counter: _Counter, failures: list[FailureSample]
) -> None:
    expected = case.expected.pricing
    if expected is None:
        return
    actual = case.actual.pricing
    expected_value = _pricing_key(expected)
    actual_value = _pricing_key(actual) if actual else "缺失"
    passed = actual is not None and expected_value == actual_value
    counter.add(passed)
    if not passed:
        failures.append(
            _failure(case, "pricing_accuracy", expected_value, actual_value)
        )


def _evaluate_citations(
    case: GoldenCase, counter: _Counter, failures: list[FailureSample]
) -> None:
    expected = case.expected.citation
    if expected is None:
        return
    reasons = (
        {item.reason_id: item for item in case.actual.citation.reasons}
        if case.actual.citation
        else {}
    )
    for reason_id in expected.required_reason_ids:
        reason = reasons.get(reason_id)
        passed = reason is not None and bool(reason.evidence_ids or reason.fact_ids)
        counter.add(passed)
        if not passed:
            failures.append(
                _failure(case, "citation_completeness", "至少一个合法引用", reason_id)
            )


def _pricing_key(snapshot: PricingSnapshot) -> str:
    return f"{snapshot.final_price}|{','.join(snapshot.promotion_ids)}"


def _failure(
    case: GoldenCase, metric: str, expected: object, actual: object
) -> FailureSample:
    return FailureSample(
        case_id=case.case_id,
        metric=metric,
        expected=_display(expected),
        actual=_display(actual),
    )


def _display(value: object) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def _collect_versions(cases: list[GoldenCase]) -> ReportVersions:
    return ReportVersions(
        dataset_versions=sorted({case.versions.dataset_version for case in cases}),
        prompt_versions=sorted({case.versions.prompt_version for case in cases}),
        pricing_rule_versions=sorted(
            {case.versions.pricing_rule_version for case in cases}
        ),
        scoring_versions=sorted({case.versions.scoring_version for case in cases}),
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="运行 HengPick Golden Dataset 确定性评测"
    )
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = evaluate_cases(load_cases(args.cases))
    content = (
        json.dumps(report.model_dump(by_alias=True), ensure_ascii=False, indent=2)
        + "\n"
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content, encoding="utf-8")
    else:
        print(content, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
