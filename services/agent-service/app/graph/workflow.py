from dataclasses import dataclass

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.graph.nodes import ShoppingDecisionNodes, route_after_clarification, route_after_product
from app.graph.state import ShoppingDecisionState
from app.intent.prompt import IntentPrompt
from app.tools.client import CommerceToolClient


@dataclass(frozen=True)
class StubGraphModel:
    """路由测试使用的可控模型，不产生金额、分数或标识。"""

    report_text: str = "基于确定性结果生成的骨架报告"
    attempted_amount: str | None = None
    attempted_score: int | None = None
    intent_output: dict[str, object] | None = None

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]:
        return self.intent_output or {
            "category": "PHONE",
            "recipient": None,
            "budget": {"max": "5000.00", "currency": "CNY"},
            "hard_constraints": [],
            "usage_scenarios": ["DAILY_USE"],
            "preferences": [],
            "memberships": [],
            "inferences": [],
        }

    def compose_report(self, state: ShoppingDecisionState) -> dict[str, object]:
        return {
            "generation_type": "STUB",
            "summary": self.report_text,
            "recommendations": [],
        }


def build_shopping_decision_graph(
    model: StubGraphModel, tool_client: CommerceToolClient
) -> CompiledStateGraph:
    """构建接入真实 Commerce Tool 契约的商品主链。"""

    nodes = ShoppingDecisionNodes(model, tool_client)
    builder = StateGraph(ShoppingDecisionState)
    builder.add_node("load_context", nodes.load_context)
    builder.add_node("intent", nodes.intent)
    builder.add_node("clarification", nodes.clarification)
    builder.add_node("product", nodes.product)
    builder.add_node("review_stub", nodes.review_stub)
    builder.add_node("price", nodes.price)
    builder.add_node("score", nodes.score)
    builder.add_node("report_stub", nodes.report_stub)
    builder.add_node("validate", nodes.validate)
    builder.add_node("no_result", nodes.no_result)
    builder.add_edge(START, "load_context")
    builder.add_edge("load_context", "intent")
    builder.add_edge("intent", "clarification")
    builder.add_conditional_edges(
        "clarification", route_after_clarification, {"wait_for_user": END, "product": "product"}
    )
    builder.add_conditional_edges("product", route_after_product)
    builder.add_edge("review_stub", "price")
    builder.add_edge("price", "score")
    builder.add_edge("score", "report_stub")
    builder.add_edge("report_stub", "validate")
    builder.add_edge("validate", END)
    builder.add_edge("no_result", END)
    return builder.compile()
