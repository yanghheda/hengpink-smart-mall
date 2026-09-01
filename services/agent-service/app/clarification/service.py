from copy import deepcopy
from typing import Any

from app.clarification.models import ClarificationPlan, ClarificationQuestion

QUESTION_THRESHOLD = 0.35
MAX_CLARIFICATION_ROUNDS = 2
CONFIDENCE_PENALTY = "0.15"


def _merge_collection(
    previous: list[dict[str, Any]] | list[str],
    current: list[dict[str, Any]] | list[str],
    key: str | None = None,
) -> list[dict[str, Any]] | list[str]:
    """按稳定业务键合并集合，新一轮的显式值覆盖旧值。"""

    if not current:
        return deepcopy(previous)
    if key is None:
        return list(dict.fromkeys([*previous, *current]))
    merged = {str(item[key]): deepcopy(item) for item in previous}
    merged.update({str(item[key]): deepcopy(item) for item in current})
    return list(merged.values())


def merge_intents(previous: dict[str, Any] | None, current: dict[str, Any]) -> dict[str, Any]:
    """用持久化旧 Intent 补齐本轮未表达字段，且不修改输入快照。"""

    if previous is None:
        return deepcopy(current)
    merged = deepcopy(previous)
    scalar_fields = ("category", "recipient", "budget")
    for field in scalar_fields:
        if current.get(field) is not None:
            merged[field] = deepcopy(current[field])
    collection_keys = {
        "hard_constraints": "name",
        "preferences": "name",
        "inferences": "name",
        "usage_scenarios": None,
        "memberships": None,
    }
    for field, key in collection_keys.items():
        merged[field] = _merge_collection(previous.get(field, []), current.get(field, []), key)
    return merged


def _requests_direct_recommendation(messages: list[dict[str, str]]) -> bool:
    text = " ".join(item["content"] for item in messages if item.get("role") == "user")
    return any(term in text for term in ("直接推荐", "直接给结果", "不用再问", "别问了"))


class ClarificationPlanner:
    """用可测试规则决定是否追问，模型不决定阈值、轮次或置信惩罚。"""

    def plan(
        self,
        intent: dict[str, Any],
        messages: list[dict[str, str]],
        round_count: int,
    ) -> ClarificationPlan:
        candidates = self._candidate_questions(intent)
        hard_questions = [item for item in candidates if item.field == "category"]
        soft_questions = [item for item in candidates if item.field != "category"]

        if round_count >= MAX_CLARIFICATION_ROUNDS:
            return ClarificationPlan(
                questions=[],
                confidence_penalty=CONFIDENCE_PENALTY,
                reason="CLARIFICATION_ROUND_LIMIT_REACHED",
            )
        if _requests_direct_recommendation(messages) and not hard_questions:
            return ClarificationPlan(
                questions=[],
                confidence_penalty=CONFIDENCE_PENALTY if soft_questions else None,
                reason="USER_SKIPPED_SOFT_CLARIFICATION",
            )

        questions = [
            item
            for item in [*hard_questions, *soft_questions]
            if item.question_value >= QUESTION_THRESHOLD
        ][:2]
        return ClarificationPlan(
            questions=questions,
            reason="HIGH_VALUE_INFORMATION_MISSING" if questions else "INTENT_SUFFICIENT",
        )

    @staticmethod
    def _candidate_questions(intent: dict[str, Any]) -> list[ClarificationQuestion]:
        questions: list[ClarificationQuestion] = []
        if intent.get("category") is None:
            questions.append(
                ClarificationQuestion(
                    field="category",
                    text="你想购买哪一类商品？",
                    options=["手机", "耳机", "暂不确定"],
                    question_value=0.95,
                )
            )
        constraints = intent.get("hard_constraints", [])
        constraint_signatures: dict[str, set[tuple[str, str]]] = {}
        for constraint in constraints:
            signature = (str(constraint.get("operator")), str(constraint.get("value")))
            constraint_signatures.setdefault(str(constraint.get("name")), set()).add(signature)
        if any(len(signatures) > 1 for signatures in constraint_signatures.values()):
            questions.append(
                ClarificationQuestion(
                    field="hard_constraint_conflict",
                    text="你给出的硬性条件存在冲突，以哪一项为准？",
                    options=["保留前一项", "保留后一项", "暂不确定"],
                    question_value=0.88,
                )
            )
        if intent.get("budget") is None:
            questions.append(
                ClarificationQuestion(
                    field="budget",
                    text="你的预算范围更接近哪一档？",
                    options=["2000 元以内", "2000—4000 元", "暂不确定"],
                    question_value=0.56,
                )
            )
        if not intent.get("usage_scenarios"):
            questions.append(
                ClarificationQuestion(
                    field="usage_scenario",
                    text="最主要的使用场景是什么？",
                    options=["日常使用", "工作学习", "暂不确定"],
                    question_value=0.38,
                )
            )
        return questions
