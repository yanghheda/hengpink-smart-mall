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


class QualityJudgeSnapshot(StrictModel):
    readability_score: int = Field(ge=1, le=5)
    judge_prompt_version: str = Field(min_length=1)
    judge_model_version: str = Field(min_length=1)


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
    quality_judge: QualityJudgeSnapshot | None = None


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


class AuxiliaryQuality(StrictModel):
    auxiliary_only: bool = True
    denominator: int = Field(ge=0)
    average_readability: str | None
    judge_prompt_versions: list[str]
    judge_model_versions: list[str]


class GoldenReport(StrictModel):
    case_count: int
    case_ids: list[str]
    versions: ReportVersions
    metrics: dict[str, MetricResult]
    auxiliary_quality: AuxiliaryQuality
    failures: list[FailureSample]


class GateFailure(StrictModel):
    metric: str
    requirement: str
    actual: str


class GateResult(StrictModel):
    passed: bool
    failures: list[GateFailure]


class PromptComparison(StrictModel):
    comparable: bool
    reasons: list[str]
    baseline_prompt_versions: list[str]
    candidate_prompt_versions: list[str]
    metric_deltas: dict[str, str | None]
    readability_delta: str | None


class EvaluationOutput(StrictModel):
    report: GoldenReport
    gate: GateResult
    comparison: PromptComparison | None = None


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
        case_ids=sorted(case.case_id for case in case_list),
        versions=_collect_versions(case_list),
        metrics={name: counter.result() for name, counter in counters.items()},
        auxiliary_quality=_evaluate_auxiliary_quality(case_list),
        failures=failures,
    )


def _evaluate_auxiliary_quality(cases: list[GoldenCase]) -> AuxiliaryQuality:
    """汇总外部 Judge 快照；该结果不参与发布门禁。"""
    snapshots = [
        case.actual.quality_judge
        for case in cases
        if case.actual.quality_judge is not None
    ]
    average = None
    if snapshots:
        total = sum(item.readability_score for item in snapshots)
        average = f"{Decimal(total) / Decimal(len(snapshots)):.4f}"
    return AuxiliaryQuality(
        denominator=len(snapshots),
        average_readability=average,
        judge_prompt_versions=sorted({item.judge_prompt_version for item in snapshots}),
        judge_model_versions=sorted({item.judge_model_version for item in snapshots}),
    )


def evaluate_gate(report: GoldenReport) -> GateResult:
    """只用确定性指标执行 Demo 回归门禁，缺少样本时关闭放行。"""
    requirements = {
        "hard_constraint_violation_rate": ("0.0000", "必须等于 0"),
        "pricing_accuracy": ("1.0000", "必须等于 1"),
        "citation_completeness": ("1.0000", "必须等于 1"),
    }
    failures: list[GateFailure] = []
    for metric_name, (expected_value, description) in requirements.items():
        metric = report.metrics[metric_name]
        if metric.denominator == 0 or metric.value != expected_value:
            failures.append(
                GateFailure(
                    metric=metric_name,
                    requirement=f"{description} 且分母大于 0",
                    actual=metric.value if metric.value is not None else "不可计算",
                )
            )
    if any(item.metric == "dataset_version_match" for item in report.failures):
        failures.append(
            GateFailure(
                metric="dataset_version_match",
                requirement="所有 Case 的数据版本必须一致",
                actual="存在版本不匹配",
            )
        )
    return GateResult(passed=not failures, failures=failures)


def compare_reports(
    baseline: GoldenReport, candidate: GoldenReport
) -> PromptComparison:
    """在固定 Case 和确定性版本上比较 Prompt 候选，防止换题比较。"""
    reasons: list[str] = []
    if baseline.case_ids != candidate.case_ids:
        reasons.append("Case 集不一致")
    deterministic_versions = (
        "dataset_versions",
        "pricing_rule_versions",
        "scoring_versions",
    )
    for field in deterministic_versions:
        if getattr(baseline.versions, field) != getattr(candidate.versions, field):
            reasons.append(f"确定性版本不一致：{field}")
    comparable = not reasons
    metric_deltas = {
        name: _decimal_delta(baseline.metrics[name].value, metric.value)
        if comparable
        else None
        for name, metric in candidate.metrics.items()
    }
    readability_delta = None
    if comparable:
        readability_delta = _decimal_delta(
            baseline.auxiliary_quality.average_readability,
            candidate.auxiliary_quality.average_readability,
        )
    return PromptComparison(
        comparable=comparable,
        reasons=reasons,
        baseline_prompt_versions=baseline.versions.prompt_versions,
        candidate_prompt_versions=candidate.versions.prompt_versions,
        metric_deltas=metric_deltas,
        readability_delta=readability_delta,
    )


def _decimal_delta(baseline: str | None, candidate: str | None) -> str | None:
    if baseline is None or candidate is None:
        return None
    return f"{Decimal(candidate) - Decimal(baseline):.4f}"


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
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--gate", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = evaluate_cases(load_cases(args.cases))
    comparison = None
    if args.baseline:
        comparison = compare_reports(evaluate_cases(load_cases(args.baseline)), report)
    gate = evaluate_gate(report)
    result: GoldenReport | EvaluationOutput = report
    if args.gate or comparison is not None:
        result = EvaluationOutput(report=report, gate=gate, comparison=comparison)
    content = (
        json.dumps(result.model_dump(by_alias=True), ensure_ascii=False, indent=2)
        + "\n"
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content, encoding="utf-8")
    else:
        print(content, end="")
    if args.gate and (not gate.passed or (comparison and not comparison.comparable)):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
