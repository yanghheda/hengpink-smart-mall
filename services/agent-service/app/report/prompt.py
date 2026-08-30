from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.report.models import DecisionReportNarrative


@dataclass(frozen=True)
class DecisionReportPrompt:
    version: str
    content: str
    output_schema: dict[str, Any]


def load_decision_report_prompt() -> DecisionReportPrompt:
    """加载固定版本 Prompt，并由代码中的 Pydantic 模型提供输出 Schema。"""

    prompt_path = Path(__file__).parents[1] / "prompts" / "decision-report" / "v1.yaml"
    return DecisionReportPrompt(
        version="decision-report-v1",
        content=prompt_path.read_text(encoding="utf-8"),
        output_schema=DecisionReportNarrative.model_json_schema(),
    )
