from app.intent.models import IntentCategory, IntentSource
from app.intent.prompt import IntentPrompt
from app.intent.service import IntentParser


class SequencedIntentModel:
    def __init__(self, outputs: list[dict[str, object]]) -> None:
        self.outputs = outputs
        self.calls: list[tuple[str, str, dict[str, object] | None]] = []

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]:
        self.calls.append((messages[-1]["content"], prompt.version, repair_context))
        return self.outputs.pop(0)


def valid_intent() -> dict[str, object]:
    return {
        "category": "PHONE",
        "recipient": "PARENTS",
        "budget": {"min": "2000.00", "max": "3000.00", "currency": "CNY"},
        "hard_constraints": [
            {"name": "storage_gb", "operator": "GTE", "value": "256", "source": "USER_EXPLICIT"}
        ],
        "usage_scenarios": ["DAILY_COMMUNICATION"],
        "preferences": [{"name": "BATTERY", "weight": "0.60", "source": "USER_EXPLICIT"}],
        "memberships": ["SMART_MALL_PLUS"],
        "inferences": [
            {"name": "EASY_TO_USE", "reason": "用户为父母购买", "source": "SYSTEM_INFERRED"}
        ],
    }


def test_valid_structured_intent_is_accepted_without_repair() -> None:
    model = SequencedIntentModel([valid_intent()])

    result = IntentParser(model).parse([{"role": "user", "content": "给父母买手机"}])

    assert result.intent.category is IntentCategory.PHONE
    assert result.intent.hard_constraints[0].source is IntentSource.USER_EXPLICIT
    assert result.trace.attempt_count == 1
    assert result.trace.fallback_used is False
    assert model.calls[0][1] == "intent-v1"


def test_invalid_enum_and_amount_are_repaired_once_and_original_error_is_traced() -> None:
    invalid = valid_intent()
    invalid["category"] = "CAR"
    invalid["budget"] = {"min": "两千", "max": "2999.999", "currency": "CNY"}
    model = SequencedIntentModel([invalid, valid_intent()])

    result = IntentParser(model).parse([{"role": "user", "content": "预算三千内的手机"}])

    assert result.trace.attempt_count == 2
    assert result.trace.repair_used is True
    assert result.trace.original_error_code == "INTENT_SCHEMA_INVALID"
    assert "category" in result.trace.original_error_fields
    assert "budget" in result.trace.original_error_fields
    assert model.calls[1][2] is not None


def test_inference_cannot_masquerade_as_hard_constraint() -> None:
    invalid = valid_intent()
    invalid["hard_constraints"] = [
        {"name": "easy_to_use", "operator": "EQ", "value": True, "source": "SYSTEM_INFERRED"}
    ]
    model = SequencedIntentModel([invalid, valid_intent()])

    result = IntentParser(model).parse([{"role": "user", "content": "给父母买手机"}])

    assert result.trace.repair_used is True
    assert "hard_constraints" in result.trace.original_error_fields
    assert all(item.source is IntentSource.USER_EXPLICIT for item in result.intent.hard_constraints)


def test_second_invalid_output_uses_explicit_rule_fallback_and_keeps_both_errors() -> None:
    invalid = valid_intent()
    invalid["category"] = "UNKNOWN"
    model = SequencedIntentModel([invalid, invalid])

    result = IntentParser(model).parse(
        [{"role": "user", "content": "给爸妈买一台3000元以内的手机"}]
    )

    assert result.intent.category is IntentCategory.PHONE
    assert result.intent.budget is not None
    assert str(result.intent.budget.max) == "3000.00"
    assert result.trace.attempt_count == 2
    assert result.trace.fallback_used is True
    assert result.trace.warning_code == "INTENT_SCHEMA_FALLBACK"
    assert len(result.trace.validation_errors) == 2
