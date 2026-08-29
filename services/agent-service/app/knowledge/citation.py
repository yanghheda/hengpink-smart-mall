from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from app.knowledge.retrieval import RetrievedEvidence


@dataclass(frozen=True)
class CitationTarget:
    dataset_version: str
    product_id: str
    sku_id: str


@dataclass(frozen=True)
class DraftReason:
    reason_id: str
    topic: str
    statement: str
    evidence_ids: tuple[str, ...] = ()
    fact_ids: tuple[str, ...] = ()


@dataclass(frozen=True)
class ValidatedReason:
    reason_id: str
    topic: str
    statement: str
    evidence_ids: tuple[str, ...]
    fact_ids: tuple[str, ...]


@dataclass(frozen=True)
class CitationIssue:
    reason_id: str
    evidence_id: str
    code: str


@dataclass(frozen=True)
class CitationValidationResult:
    reasons: list[ValidatedReason]
    issues: list[CitationIssue]
    removed_reason_ids: list[str]


def validate_citations(
    reasons: Sequence[DraftReason],
    *,
    available_evidence: Sequence[RetrievedEvidence],
    target: CitationTarget,
) -> CitationValidationResult:
    """只允许理由引用本次模型上下文中归属和主题均合法的证据。"""
    evidence_by_id = {item.evidence_id: item for item in available_evidence}
    validated: list[ValidatedReason] = []
    issues: list[CitationIssue] = []
    removed_reason_ids: list[str] = []

    for reason in reasons:
        legal_ids: list[str] = []
        for evidence_id in dict.fromkeys(reason.evidence_ids):
            evidence = evidence_by_id.get(evidence_id)
            issue_code = _validate_evidence(evidence, reason.topic, target)
            if issue_code:
                issues.append(CitationIssue(reason.reason_id, evidence_id, issue_code))
            else:
                legal_ids.append(evidence_id)

        if not legal_ids and not reason.fact_ids:
            removed_reason_ids.append(reason.reason_id)
            continue
        validated.append(
            ValidatedReason(
                reason_id=reason.reason_id,
                topic=reason.topic,
                statement=reason.statement,
                evidence_ids=tuple(legal_ids),
                fact_ids=reason.fact_ids,
            )
        )

    return CitationValidationResult(validated, issues, removed_reason_ids)


def _validate_evidence(
    evidence: RetrievedEvidence | None,
    reason_topic: str,
    target: CitationTarget,
) -> str | None:
    if evidence is None:
        return "CITATION_NOT_IN_MODEL_CONTEXT"
    payload = evidence.payload
    if payload.get("dataset_version") != target.dataset_version:
        return "CITATION_DATASET_MISMATCH"
    if payload.get("product_id") != target.product_id:
        return "CITATION_PRODUCT_MISMATCH"
    if payload.get("sku_id") not in (None, "", target.sku_id):
        return "CITATION_SKU_MISMATCH"
    if payload.get("topic") != reason_topic:
        return "CITATION_TOPIC_MISMATCH"
    if bool(payload.get("injection_flag", False)):
        return "CITATION_INJECTION_BLOCKED"
    return None
