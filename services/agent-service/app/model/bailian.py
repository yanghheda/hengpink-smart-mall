import json
import logging
from copy import deepcopy
from typing import Any
from urllib.request import Request, build_opener

from app.graph.state import ShoppingDecisionState
from app.intent.models import StructuredIntent
from app.intent.prompt import IntentPrompt
from app.report.prompt import load_decision_report_prompt

logger = logging.getLogger("hengpick.agent.model.bailian")


def bailian_compatible_schema(schema: dict[str, Any]) -> dict[str, Any]:
    """移除百炼正则引擎不支持的 lookahead，最终结果仍由 Pydantic 校验。"""

    compatible = deepcopy(schema)

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            pattern = value.get("pattern")
            if isinstance(pattern, str) and "(?!" in pattern:
                value["pattern"] = r"^-?[0-9]+([.][0-9]+)?$"
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(compatible)
    return compatible


class BailianModel:
    """通过百炼 OpenAI 兼容接口执行受 Schema 约束的生成。"""

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str,
        model_name: str,
        reasoning_effort: str = "low",
        timeout_seconds: float = 20,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model_name = model_name
        self._reasoning_effort = reasoning_effort
        self._timeout_seconds = timeout_seconds
        self._opener = build_opener()

    def generate_intent(
        self,
        messages: list[dict[str, str]],
        prompt: IntentPrompt,
        repair_context: dict[str, object] | None = None,
    ) -> dict[str, object]:
        system = prompt.content
        if repair_context:
            system += f"\n修复要求：{json.dumps(repair_context, ensure_ascii=False)}"
        try:
            return self._structured_completion(
                messages=[{"role": "system", "content": system}, *messages],
                schema_name="shopping_intent",
                schema=StructuredIntent.model_json_schema(),
            )
        except (OSError, ValueError, TypeError, KeyError, IndexError) as error:
            logger.warning("bailian_intent_fallback errorType=%s", type(error).__name__)
            return {}

    def compose_report(self, state: ShoppingDecisionState) -> dict[str, Any]:
        report_prompt = load_decision_report_prompt()
        candidates = []
        for index, card in enumerate(state["score_cards"][:3]):
            sku_id = str(card.get("skuId", ""))
            candidate = next(
                (item for item in state["candidates"] if item.get("skuId") == sku_id),
                {},
            )
            candidates.append(
                {
                    "slot": ("PRIMARY", "ALTERNATIVE_1", "ALTERNATIVE_2")[index],
                    "candidate": candidate,
                    "score_card": card,
                    "evidence": state["evidence"].get(sku_id, []),
                }
            )
        payload = {
            "intent_summary": state.get("intent") or {},
            "candidates": candidates,
        }
        messages = [
            {"role": "system", "content": report_prompt.content},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False, separators=(",", ":"))},
        ]
        last_error: Exception | None = None
        for attempt in range(2):
            try:
                return self._structured_completion(
                    messages=messages,
                    schema_name="shopping_decision_report",
                    schema=report_prompt.output_schema,
                )
            except (OSError, ValueError, TypeError, KeyError, IndexError) as error:
                last_error = error
                logger.warning(
                    "bailian_report_attempt_failed attempt=%s errorType=%s error=%s",
                    attempt + 1,
                    type(error).__name__,
                    str(error)[:300],
                )
        logger.warning("bailian_report_fallback errorType=%s", type(last_error).__name__)
        return {
            "generation_type": "TEMPLATE_FALLBACK",
            "summary": "AI 解释暂不可用，已返回基于确定性评分的基础分析。",
            "recommendations": [],
        }

    def _structured_completion(
        self,
        *,
        messages: list[dict[str, str]],
        schema_name: str,
        schema: dict[str, Any],
    ) -> dict[str, Any]:
        body = json.dumps(
            {
                "model": self._model_name,
                "messages": messages,
                "reasoning_effort": self._reasoning_effort,
                "response_format": {
                    "type": "json_schema",
                    "json_schema": {
                        "name": schema_name,
                        "strict": True,
                        "schema": bailian_compatible_schema(schema),
                    },
                },
            },
            ensure_ascii=False,
        ).encode("utf-8")
        request = Request(
            f"{self._base_url}/chat/completions",
            data=body,
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with self._opener.open(request, timeout=self._timeout_seconds) as response:
            payload = json.load(response)
        content = payload["choices"][0]["message"]["content"]
        if not isinstance(content, str):
            raise TypeError("百炼模型未返回结构化文本")
        normalized = content.strip()
        if normalized.startswith("```"):
            normalized = normalized.removeprefix("```json").removeprefix("```")
            normalized = normalized.removesuffix("```").strip()
        parsed = json.loads(normalized)
        if not isinstance(parsed, dict):
            raise TypeError("百炼模型结构化输出必须是对象")
        return parsed
