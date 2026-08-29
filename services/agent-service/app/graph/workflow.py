from dataclasses import dataclass

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.graph.nodes import ShoppingDecisionNodes, route_after_product
from app.graph.state import ShoppingDecisionState
from app.intent.prompt import IntentPrompt


@dataclass(frozen=True)
class StubGraphModel:
    """路由测试使用的可控模型，不产生金额、分数或标识。"""

    candidate_ids: tuple[str, ...] | list[str] = ("sku-stub-1",)
    report_text: str = "基于确定性结果生成的骨架报告"
    attempted_amount: str | None = None
    attempted_score: int | None = None

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]:
        return {
            "category": "PHONE",
            "recipient": None,
            "budget": None,
            "hard_constraints": [],
            "usage_scenarios": [],
            "preferences": [],
            "memberships": [],
            "inferences": [],
        }

    def stub_candidate_ids(self) -> list[str]:
        return list(self.candidate_ids)

    def compose_report(self, candidate_ids: list[str]) -> str:
        return self.report_text


def build_shopping_decision_graph(model: StubGraphModel) -> CompiledStateGraph:
    """构建主链骨架，并接入 P10-S02 Intent 严格边界。"""

    nodes = ShoppingDecisionNodes(model)
    builder = StateGraph(ShoppingDecisionState)
    builder.add_node("load_context", nodes.load_context)
    builder.add_node("intent", nodes.intent)
    builder.add_node("product", nodes.product)
    builder.add_node("review_stub", nodes.review_stub)
    builder.add_node("price", nodes.price)
    builder.add_node("score", nodes.score)
    builder.add_node("report_stub", nodes.report_stub)
    builder.add_node("validate", nodes.validate)
    builder.add_node("no_result", nodes.no_result)
    builder.add_edge(START, "load_context")
    builder.add_edge("load_context", "intent")
    builder.add_edge("intent", "product")
    builder.add_conditional_edges("product", route_after_product)
    builder.add_edge("review_stub", "price")
    builder.add_edge("price", "score")
    builder.add_edge("score", "report_stub")
    builder.add_edge("report_stub", "validate")
    builder.add_edge("validate", END)
    builder.add_edge("no_result", END)
    return builder.compile()
