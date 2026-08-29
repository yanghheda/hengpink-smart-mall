from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ClarificationQuestion(BaseModel):
    """可展示、可持久化的确定性追问。"""

    model_config = ConfigDict(extra="forbid")
    field: Literal["category", "hard_constraint_conflict", "budget", "usage_scenario"]
    text: str = Field(min_length=1, max_length=120)
    options: list[str] = Field(min_length=2, max_length=4)
    question_value: float = Field(ge=0, le=1)


class ClarificationPlan(BaseModel):
    """路由所需的最小追问决策，不包含模型私有推理。"""

    model_config = ConfigDict(extra="forbid")
    questions: list[ClarificationQuestion] = Field(max_length=2)
    confidence_penalty: str | None = None
    reason: str
