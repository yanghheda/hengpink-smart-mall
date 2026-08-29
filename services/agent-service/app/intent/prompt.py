from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class IntentPrompt:
    version: str
    content: str


def load_intent_prompt() -> IntentPrompt:
    """从随代码发布的固定路径加载 Prompt，避免运行时静默切换版本。"""

    prompt_path = Path(__file__).parents[1] / "prompts" / "intent" / "v1.yaml"
    return IntentPrompt(version="intent-v1", content=prompt_path.read_text(encoding="utf-8"))
