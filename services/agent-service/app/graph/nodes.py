from dataclasses import dataclass
from typing import Protocol

from app.clarification.service import ClarificationPlanner, merge_intents
from app.graph.state import ShoppingDecisionState
from app.intent.prompt import IntentPrompt
from app.intent.service import IntentParser
from app.tools.client import CommerceToolClient


class GraphModel(Protocol):
    """模型适配边界；节点不感知具体供应商。"""

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]: ...

    def compose_report(self, candidate_ids: list[str]) -> str: ...


@dataclass(frozen=True)
class ShoppingDecisionNodes:
    """商品事实经受控 Tool Client 进入的确定性主链。"""

    model: GraphModel
    tool_client: CommerceToolClient

    def load_context(self, state: ShoppingDecisionState) -> dict[str, object]:
        return {"completed_nodes": ["load_context"]}

    def intent(self, state: ShoppingDecisionState) -> dict[str, object]:
        result = IntentParser(self.model).parse(state["messages"])
        merged_intent = merge_intents(
            state.get("previous_intent"), result.intent.model_dump(mode="json")
        )
        warnings = list(state["warnings"])
        if result.trace.warning_code:
            warnings.append(result.trace.warning_code)
        return {
            "intent": merged_intent,
            "intent_trace": result.trace.model_dump(mode="json"),
            "warnings": warnings,
            "completed_nodes": ["intent"],
        }

    def clarification(self, state: ShoppingDecisionState) -> dict[str, object]:
        plan = ClarificationPlanner().plan(
            state["intent"] or {}, state["messages"], state["clarification_round"]
        )
        warnings = list(state["warnings"])
        if plan.confidence_penalty:
            warnings.append(plan.reason)
        return {
            "clarification": plan.model_dump(mode="json"),
            "confidence_penalty": plan.confidence_penalty,
            "warnings": warnings,
            "completed_nodes": ["clarification"],
        }

    def product(self, state: ShoppingDecisionState) -> dict[str, object]:
        intent = state["intent"] or {}
        search = self._call(
            state,
            "search_products",
            "product-search",
            {
                "categoryId": intent.get("category"),
                "budget": intent.get("budget"),
                "hardConstraints": intent.get("hard_constraints", []),
                "limit": 30,
            },
        )
        matched = search.get("matchedCandidates", [])
        selected = matched[:6]
        specs = (
            self._call(state, "get_product_specs", "product-specs", {"candidates": selected})
            if selected
            else {"candidates": []}
        )
        return {
            "candidates": specs.get("candidates", []),
            "tool_calls": self._tool_traces(),
            "completed_nodes": ["product"],
        }

    def review_stub(self, state: ShoppingDecisionState) -> dict[str, object]:
        evidence = {
            candidate["skuId"]: [{"evidence_id": f"stub-{candidate['skuId']}"}]
            for candidate in state["candidates"]
        }
        return {"evidence": evidence, "completed_nodes": ["review_stub"]}

    def price(self, state: ShoppingDecisionState) -> dict[str, object]:
        sku_ids = [candidate["skuId"] for candidate in state["candidates"]]
        offers = self._call(state, "get_price_offers", "price-offers", {"skuIds": sku_ids})
        plans = self._call(
            state,
            "calculate_final_price",
            "final-price",
            {
                "offers": offers.get("offers", []),
                "memberships": (state["intent"] or {}).get("memberships", []),
            },
        )
        return {
            "price_plans": plans.get("pricePlans", {}),
            "tool_calls": self._tool_traces(),
            "completed_nodes": ["price"],
        }

    def score(self, state: ShoppingDecisionState) -> dict[str, object]:
        result = self._call(
            state,
            "score_candidates",
            "candidate-score",
            {
                "intent": state["intent"],
                "candidates": state["candidates"],
                "pricePlans": state["price_plans"],
            },
        )
        return {
            "score_cards": result.get("scoreCards", []),
            "tool_calls": self._tool_traces(),
            "completed_nodes": ["score"],
        }

    def report_stub(self, state: ShoppingDecisionState) -> dict[str, object]:
        candidate_ids = [card["skuId"] for card in state["score_cards"]]
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
        known_ids = [candidate["skuId"] for candidate in state["candidates"]]
        return {
            "validation": {"valid": bool(report_ids) and report_ids == known_ids},
            "completed_nodes": ["validate"],
        }

    def no_result(self, state: ShoppingDecisionState) -> dict[str, object]:
        return {
            "warnings": [*state["warnings"], "NO_MATCHED_CANDIDATE"],
            "completed_nodes": ["no_result"],
        }

    def _call(
        self,
        state: ShoppingDecisionState,
        tool_name: str,
        call_suffix: str,
        input_data: dict[str, object],
    ) -> dict[str, object]:
        return self.tool_client.call(
            tool_name,
            run_id=state["run_id"],
            run_version=state["run_version"],
            tool_call_id=f"{state['run_id']}:{call_suffix}",
            dataset_version=state["dataset_version"],
            input_data=input_data,
        )

    def _tool_traces(self) -> list[dict[str, object]]:
        return [trace.model_dump() for trace in self.tool_client.traces]


def route_after_product(state: ShoppingDecisionState) -> str:
    """零候选必须终止，禁止生成违规首选。"""

    return "review_stub" if state["candidates"] else "no_result"


def route_after_clarification(state: ShoppingDecisionState) -> str:
    """有问题时在唯一等待点停止，否则继续确定性商品主链。"""

    clarification = state.get("clarification") or {}
    return "wait_for_user" if clarification.get("questions") else "product"
