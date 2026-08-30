from copy import deepcopy
from typing import Any


class ReportFallbackService:
    """用确定性候选生成可见降级报告，不重新计算业务事实。"""

    def for_model_failure(self, ranked_candidates: list[dict[str, Any]]) -> dict[str, Any]:
        """模型不可用时返回完整基础报告，并明确标记模板来源。"""

        return self._build(
            ranked_candidates,
            status="COMPLETED",
            generation_type="TEMPLATE_FALLBACK",
            missing_modules=[],
            degradation_reasons=["MODEL_UNAVAILABLE"],
            user_notice="AI 解释暂不可用，已返回基础分析",
            omit_evidence=False,
        )

    def for_rag_failure(self, ranked_candidates: list[dict[str, Any]]) -> dict[str, Any]:
        """证据服务不可用时只展示仍可核验的结构化事实。"""

        return self._build(
            ranked_candidates,
            status="PARTIAL",
            generation_type="PARTIAL_TEMPLATE_FALLBACK",
            missing_modules=["RAG_EVIDENCE"],
            degradation_reasons=["QDRANT_UNAVAILABLE"],
            user_notice="评价证据暂不完整，已返回结构化基础分析",
            omit_evidence=True,
        )

    def _build(
        self,
        ranked_candidates: list[dict[str, Any]],
        *,
        status: str,
        generation_type: str,
        missing_modules: list[str],
        degradation_reasons: list[str],
        user_notice: str,
        omit_evidence: bool,
    ) -> dict[str, Any]:
        if not ranked_candidates:
            raise ValueError("缺少确定性候选，不能生成模板报告")

        recommendations = []
        for rank, candidate in enumerate(ranked_candidates[:3], start=1):
            projected = deepcopy(candidate)
            projected["rank"] = rank
            if omit_evidence:
                projected["evidence"] = []
            projected["template_reason"] = self._template_reason(projected)
            recommendations.append(projected)

        return {
            "status": status,
            "generation_type": generation_type,
            "user_notice": user_notice,
            "missing_modules": list(missing_modules),
            "degradation_reasons": list(degradation_reasons),
            "recommendations": recommendations,
        }

    @staticmethod
    def _template_reason(candidate: dict[str, Any]) -> str:
        facts = candidate.get("facts", [])
        if facts and isinstance(facts[0], dict) and facts[0].get("statement"):
            return f"该候选排名来自确定性评分；已核验事实：{facts[0]['statement']}。"
        return "该候选排名来自确定性评分；当前没有更多可展示的事实说明。"
