from __future__ import annotations

import json
import re

PROMPT_INJECTION_WARNING = "PROMPT_INJECTION_SUSPECTED"

_INJECTION_PATTERNS = (
    re.compile(r"忽略.{0,12}(之前|此前|所有|系统).{0,8}(指令|规则|提示)", re.IGNORECASE),
    re.compile(r"(泄露|显示|输出).{0,12}(system\s*prompt|系统提示|系统指令)", re.IGNORECASE),
    re.compile(r"ignore.{0,16}(previous|prior|all).{0,12}(instruction|prompt|rule)", re.IGNORECASE),
    re.compile(r"(调用|call|invoke).{0,20}(工具|tool|function)", re.IGNORECASE),
)


def scan_prompt_injection(content: str) -> bool:
    """识别知识文本中的明确指令型注入模式。"""
    normalized = " ".join(content.split())
    return any(pattern.search(normalized) for pattern in _INJECTION_PATTERNS)


def wrap_untrusted_evidence(content: str) -> str:
    """把证据编码为不可闭合标签的数据块，供后续模型节点安全传递。"""
    encoded = json.dumps({"content": content}, ensure_ascii=False).replace("<", "\\u003c")
    return f"<untrusted_evidence>{encoded}</untrusted_evidence>"
