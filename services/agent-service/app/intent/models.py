from decimal import Decimal, InvalidOperation
from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class IntentCategory(StrEnum):
    PHONE = "PHONE"
    HEADPHONE = "HEADPHONE"
    MONITOR = "MONITOR"
    AIR_PURIFIER = "AIR_PURIFIER"
    OFFICE_CHAIR = "OFFICE_CHAIR"


class IntentSource(StrEnum):
    USER_EXPLICIT = "USER_EXPLICIT"
    SYSTEM_INFERRED = "SYSTEM_INFERRED"


def parse_decimal_string(value: Any, field_name: str) -> Decimal:
    """金额与权重只接受十进制字符串，避免二进制浮点悄悄改值。"""

    if not isinstance(value, str):
        # Pydantic 只会把 ValueError 稳定收敛为字段校验错误。
        raise ValueError(f"{field_name} 必须是十进制字符串")  # noqa: TRY004
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError(f"{field_name} 不是合法十进制数") from exc
    if not parsed.is_finite():
        raise ValueError(f"{field_name} 必须是有限数")
    return parsed


class IntentBudget(BaseModel):
    model_config = ConfigDict(extra="forbid")
    min: Decimal | None = None
    max: Decimal | None = None
    currency: Literal["CNY"] = "CNY"

    @field_validator("min", "max", mode="before")
    @classmethod
    def validate_amount(cls, value: Any, info: Any) -> Decimal | None:
        if value is None:
            return None
        parsed = parse_decimal_string(value, info.field_name)
        if parsed < 0 or parsed.as_tuple().exponent < -2:
            raise ValueError(f"{info.field_name} 必须非负且最多两位小数")
        return parsed.quantize(Decimal("0.01"))

    @model_validator(mode="after")
    def validate_range(self) -> "IntentBudget":
        if self.min is None and self.max is None:
            raise ValueError("预算上下界不能同时为空")
        if self.min is not None and self.max is not None and self.min > self.max:
            raise ValueError("预算下限不能大于上限")
        return self


class HardConstraint(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=64)
    operator: Literal["EQ", "NE", "GTE", "LTE", "IN", "NOT_IN"]
    value: str | int | list[str]
    source: Literal[IntentSource.USER_EXPLICIT]


class IntentPreference(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=64)
    weight: Decimal = Field(ge=Decimal(0), le=Decimal(1))
    source: IntentSource

    @field_validator("weight", mode="before")
    @classmethod
    def validate_weight(cls, value: Any) -> Decimal:
        parsed = parse_decimal_string(value, "weight")
        if parsed.as_tuple().exponent < -2:
            raise ValueError("weight 最多两位小数")
        return parsed


class IntentInference(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=64)
    reason: str = Field(min_length=1, max_length=200)
    source: Literal[IntentSource.SYSTEM_INFERRED]


class StructuredIntent(BaseModel):
    model_config = ConfigDict(extra="forbid")
    category: IntentCategory | None = None
    recipient: str | None = Field(default=None, max_length=64)
    budget: IntentBudget | None = None
    hard_constraints: list[HardConstraint] = Field(default_factory=list, max_length=20)
    usage_scenarios: list[str] = Field(default_factory=list, max_length=20)
    preferences: list[IntentPreference] = Field(default_factory=list, max_length=20)
    memberships: list[str] = Field(default_factory=list, max_length=10)
    inferences: list[IntentInference] = Field(default_factory=list, max_length=20)


class IntentTrace(BaseModel):
    model_config = ConfigDict(extra="forbid")
    attempt_count: int = Field(ge=1, le=2)
    repair_used: bool
    fallback_used: bool
    original_error_code: str | None = None
    original_error_fields: list[str] = Field(default_factory=list)
    validation_errors: list[dict[str, object]] = Field(default_factory=list)
    warning_code: str | None = None


class IntentParseResult(BaseModel):
    model_config = ConfigDict(extra="forbid")
    intent: StructuredIntent
    trace: IntentTrace
