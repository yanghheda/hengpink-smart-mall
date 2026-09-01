from copy import deepcopy
from typing import Any, ClassVar

from app.report.models import CandidateSlot, DecisionReportNarrative


class DecisionReportComposer:
    """隔离模型文案与确定性事实，并在边界处重新组合。"""

    _slots: ClassVar[tuple[CandidateSlot, ...]] = tuple(CandidateSlot)

    def build_prompt_input(
        self,
        *,
        intent_summary: dict[str, Any],
        ranked_candidates: list[dict[str, Any]],
        rejected_popular_candidates: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """只暴露前三名，保留原始确定性对象供最终投影。"""

        top_candidates = deepcopy(ranked_candidates[:3])
        candidates = [
            {"slot": self._slots[index].value, **candidate}
            for index, candidate in enumerate(top_candidates)
        ]
        return {
            "intent_summary": deepcopy(intent_summary),
            "candidates": candidates,
            "rejected_popular_candidates": deepcopy(rejected_popular_candidates),
        }

    def compose(self, prompt_input: dict[str, Any], model_output: dict[str, Any]) -> dict[str, Any]:
        """严格解析文案，再按槽位注入不可由模型修改的事实。"""

        narrative = DecisionReportNarrative.model_validate(model_output)
        candidate_by_slot = {
            candidate["slot"]: candidate for candidate in prompt_input.get("candidates", [])
        }
        recommendations: list[dict[str, Any]] = []
        for item in narrative.recommendations:
            slot = item.candidate_slot.value
            if slot not in candidate_by_slot:
                raise ValueError(f"模型引用了输入中不存在的候选槽位：{slot}")
            source = candidate_by_slot[slot]
            rank = self._slots.index(item.candidate_slot) + 1
            recommendations.append(
                {
                    "rank": rank,
                    "product_id": source["product_id"],
                    "sku_id": source["sku_id"],
                    "score": source["score"],
                    "confidence": deepcopy(source["confidence"]),
                    "price_plan": deepcopy(source["price_plan"]),
                    **item.model_dump(mode="json"),
                }
            )
        return {
            "summary": narrative.summary,
            "recommendations": recommendations,
            "rejected_popular_candidates": [
                item.model_dump(mode="json") for item in narrative.rejected_popular_candidates
            ],
            "counterfactuals": narrative.counterfactuals,
            "overall_data_gaps": narrative.overall_data_gaps,
        }
