from dataclasses import dataclass
from typing import Protocol

from app.graph.state import ShoppingDecisionState
from app.intent.prompt import IntentPrompt
from app.intent.service import IntentParser


class GraphModel(Protocol):
    """模型适配边界；节点不感知具体供应商。"""

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]: ...

    def stub_candidate_ids(self) -> list[str]: ...

    def compose_report(self, candidate_ids: list[str]) -> str: ...


@dataclass(frozen=True)
class ShoppingDecisionNodes:
    """P10-S01 的确定性节点骨架。"""

    model: GraphModel

    def load_context(self, state: ShoppingDecisionState) -> dict[str, object]:
        return {"completed_nodes": ["load_context"]}

    def intent(self, state: ShoppingDecisionState) -> dict[str, object]:
        result = IntentParser(self.model).parse(state["messages"])
        warnings = list(state["warnings"])
        if result.trace.warning_code:
            warnings.append(result.trace.warning_code)
        return {
            "intent": result.intent.model_dump(mode="json"),
            "intent_trace": result.trace.model_dump(mode="json"),
            "warnings": warnings,
            "completed_nodes": ["intent"],
        }

    def product(self, state: ShoppingDecisionState) -> dict[str, object]:
        # P10-S01 骨架候选与 Intent 解耦；P10-S04 才替换为真实商品 Tool。
        candidate_ids = self.model.stub_candidate_ids()
        return {
            "candidates": [{"sku_id": sku_id} for sku_id in candidate_ids],
            "completed_nodes": ["product"],
        }

    def review_stub(self, state: ShoppingDecisionState) -> dict[str, object]:
        evidence = {
            candidate["sku_id"]: [{"evidence_id": f"stub-{candidate['sku_id']}"}]
            for candidate in state["candidates"]
        }
        return {"evidence": evidence, "completed_nodes": ["review_stub"]}

    def price(self, state: ShoppingDecisionState) -> dict[str, object]:
        plans = {
            candidate["sku_id"]: [
                {"price_plan_id": f"plan-{candidate['sku_id']}", "amount": "3999.00"}
            ]
            for candidate in state["candidates"]
        }
        return {"price_plans": plans, "completed_nodes": ["price"]}

    def score(self, state: ShoppingDecisionState) -> dict[str, object]:
        cards = [
            {"sku_id": candidate["sku_id"], "final_score": 80 - index}
            for index, candidate in enumerate(state["candidates"])
        ]
        return {"score_cards": cards, "completed_nodes": ["score"]}

    def report_stub(self, state: ShoppingDecisionState) -> dict[str, object]:
        candidate_ids = [card["sku_id"] for card in state["score_cards"]]
        return {
            "report": {
                "generation_type": "STUB",
                "summary": self.model.compose_report(candidate_ids),
                "candidate_ids": candidate_ids,
            },
            "completed_nodes": ["report_stub"],
        }

    def validate(self, state: ShoppingDecisionState) -> dict[str, object]:
        report_ids = state["report"]["candidate_ids"] if state["report"] else []
        known_ids = [candidate["sku_id"] for candidate in state["candidates"]]
        return {
            "validation": {"valid": bool(report_ids) and report_ids == known_ids},
            "completed_nodes": ["validate"],
        }

    def no_result(self, state: ShoppingDecisionState) -> dict[str, object]:
        return {
            "warnings": [*state["warnings"], "NO_MATCHED_CANDIDATE"],
            "completed_nodes": ["no_result"],
        }


def route_after_product(state: ShoppingDecisionState) -> str:
    """零候选必须终止，禁止生成违规首选。"""

    return "review_stub" if state["candidates"] else "no_result"
