import re
from typing import Protocol

from pydantic import ValidationError

from app.intent.models import (
    IntentBudget,
    IntentCategory,
    IntentParseResult,
    IntentTrace,
    StructuredIntent,
)
from app.intent.prompt import IntentPrompt, load_intent_prompt


class IntentModel(Protocol):
    """Intent 模型边界只负责结构化生成，不拥有业务事实。"""

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]: ...


def safe_validation_summary(error: ValidationError) -> dict[str, object]:
    """Trace 只保存错误位置与类型，不落模型原文或用户完整输入。"""

    fields = sorted({str(item["loc"][0]) for item in error.errors() if item["loc"]})
    types = sorted({str(item["type"]) for item in error.errors()})
    return {"code": "INTENT_SCHEMA_INVALID", "fields": fields, "types": types}


def rule_fallback(messages: list[dict[str, str]]) -> StructuredIntent:
    """二次校验失败后的保守规则降级，不把推测写成硬条件。"""

    text = " ".join(message["content"] for message in messages if message.get("role") == "user")
    category = None
    category_terms = {
        IntentCategory.PHONE: ("手机",),
        IntentCategory.HEADPHONE: ("耳机",),
        IntentCategory.MONITOR: ("显示器",),
        IntentCategory.AIR_PURIFIER: ("空气净化器", "净化器"),
        IntentCategory.OFFICE_CHAIR: ("办公椅",),
    }
    for candidate, terms in category_terms.items():
        if any(term in text for term in terms):
            category = candidate
            break

    budget = None
    range_match = re.search(
        r"(\d+(?:\.\d{1,2})?)\s*(?:元)?\s*[-—~至到]\s*(\d+(?:\.\d{1,2})?)\s*元?", text
    )
    max_match = re.search(r"(\d+(?:\.\d{1,2})?)\s*元?\s*(?:以内|以下|内)", text)
    if range_match:
        budget = IntentBudget(min=range_match.group(1), max=range_match.group(2), currency="CNY")
    elif max_match:
        budget = IntentBudget(max=max_match.group(1), currency="CNY")
    recipient = "PARENTS" if any(term in text for term in ("爸妈", "父母", "长辈")) else None
    return StructuredIntent(category=category, recipient=recipient, budget=budget)


class IntentParser:
    def __init__(self, model: IntentModel, prompt: IntentPrompt | None = None) -> None:
        self.model = model
        self.prompt = prompt or load_intent_prompt()

    def parse(self, messages: list[dict[str, str]]) -> IntentParseResult:
        errors: list[dict[str, object]] = []
        first_output = self.model.generate_intent(messages, self.prompt)
        try:
            intent = self._preserve_explicit_history(StructuredIntent.model_validate(first_output), messages)
            return IntentParseResult(
                intent=intent,
                trace=IntentTrace(attempt_count=1, repair_used=False, fallback_used=False),
            )
        except ValidationError as first_error:
            first_summary = safe_validation_summary(first_error)
            errors.append(first_summary)

        repair_context = {
            "error": first_summary,
            "instruction": "只修复 Schema 错误；不得新增用户未明确表达的硬条件。",
        }
        repaired_output = self.model.generate_intent(
            messages, self.prompt, repair_context=repair_context
        )
        try:
            intent = self._preserve_explicit_history(StructuredIntent.model_validate(repaired_output), messages)
            return IntentParseResult(
                intent=intent,
                trace=IntentTrace(
                    attempt_count=2,
                    repair_used=True,
                    fallback_used=False,
                    original_error_code="INTENT_SCHEMA_INVALID",
                    original_error_fields=list(first_summary["fields"]),
                    validation_errors=errors,
                ),
            )
        except ValidationError as second_error:
            errors.append(safe_validation_summary(second_error))
            return IntentParseResult(
                intent=rule_fallback(messages),
                trace=IntentTrace(
                    attempt_count=2,
                    repair_used=True,
                    fallback_used=True,
                    original_error_code="INTENT_SCHEMA_INVALID",
                    original_error_fields=list(first_summary["fields"]),
                    validation_errors=errors,
                    warning_code="INTENT_SCHEMA_FALLBACK",
                ),
            )

    @staticmethod
    def _preserve_explicit_history(
        model_intent: StructuredIntent, messages: list[dict[str, str]]
    ) -> StructuredIntent:
        """模型漏读短回复时，保留完整对话中可确定识别的显式事实。"""

        explicit = rule_fallback(messages)
        values = model_intent.model_dump(mode="json")
        if values.get("category") is None and explicit.category is not None:
            values["category"] = explicit.category
        if values.get("recipient") is None and explicit.recipient is not None:
            values["recipient"] = explicit.recipient
        if values.get("budget") is None and explicit.budget is not None:
            values["budget"] = explicit.budget.model_dump(mode="json")
        return StructuredIntent.model_validate(values)
