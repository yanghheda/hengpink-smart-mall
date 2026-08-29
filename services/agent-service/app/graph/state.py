import operator
from typing import Annotated, Any, TypedDict

from pydantic import BaseModel, ConfigDict, Field


class GraphMessage(BaseModel):
    """进入图前已校验的最小消息结构。"""

    model_config = ConfigDict(extra="forbid")
    role: str = Field(min_length=1)
    content: str = Field(min_length=1)


class GraphBudget(BaseModel):
    """本轮骨架只消费模型调用次数预算。"""

    model_config = ConfigDict(extra="forbid")
    max_model_calls: int = Field(ge=0, le=10)


class ShoppingDecisionState(TypedDict, total=False):
    """LangGraph 运行态；节点只返回补丁，不原地修改共享值。"""

    run_id: str
    session_id: str
    run_version: int
    user_id_ref: str
    dataset_version: str
    prompt_version: str
    scoring_version: str
    pricing_rule_version: str
    messages: list[dict[str, str]]
    budget: dict[str, int]
    intent: dict[str, Any] | None
    intent_trace: dict[str, Any] | None
    previous_intent: dict[str, Any] | None
    clarification_round: int
    clarification: dict[str, Any] | None
    confidence_penalty: str | None
    candidates: list[dict[str, Any]]
    evidence: dict[str, list[dict[str, Any]]]
    price_plans: dict[str, list[dict[str, Any]]]
    score_cards: list[dict[str, Any]]
    tool_calls: list[dict[str, Any]]
    report: dict[str, Any] | None
    validation: dict[str, Any] | None
    warnings: list[str]
    failure: dict[str, Any] | None
    completed_nodes: Annotated[list[str], operator.add]


class InitialGraphState(BaseModel):
    """Run Request 到图状态的严格边界，业务节点不重复猜测输入。"""

    model_config = ConfigDict(extra="forbid")
    run_id: str = Field(min_length=1)
    session_id: str = Field(min_length=1)
    run_version: int = Field(ge=1)
    user_id_ref: str = Field(min_length=1)
    dataset_version: str = Field(min_length=1)
    prompt_version: str = Field(min_length=1)
    scoring_version: str = Field(min_length=1)
    pricing_rule_version: str = Field(min_length=1)
    messages: list[GraphMessage]
    budget: GraphBudget
    previous_intent: dict[str, Any] | None = None
    clarification_round: int = Field(default=0, ge=0, le=2)

    def to_graph_state(self) -> ShoppingDecisionState:
        return ShoppingDecisionState(
            **self.model_dump(),
            intent=None,
            intent_trace=None,
            clarification=None,
            confidence_penalty=None,
            candidates=[],
            evidence={},
            price_plans={},
            score_cards=[],
            tool_calls=[],
            report=None,
            validation=None,
            warnings=[],
            failure=None,
            completed_nodes=[],
        )
