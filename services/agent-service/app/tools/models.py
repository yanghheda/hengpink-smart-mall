from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ToolRequestEnvelope(BaseModel):
    """发送给 Java Tool API 的统一请求信封。"""

    model_config = ConfigDict(extra="forbid")
    runId: str = Field(min_length=1)
    runVersion: int = Field(ge=1)
    toolCallId: str = Field(min_length=1)
    datasetVersion: str = Field(min_length=1)
    timeoutMs: int = Field(ge=100, le=5000)
    input: dict[str, Any]


class ToolResponseEnvelope(BaseModel):
    """Java Tool API 的严格响应信封。"""

    model_config = ConfigDict(extra="forbid")
    status: str
    data: dict[str, Any]
    sourceVersion: str
    updatedAt: str
    confidence: float = Field(ge=0, le=1)
    warnings: list[str] = []
    errorCode: str | None = None


class ToolCallTrace(BaseModel):
    """可进入 Graph State 的脱敏 Tool 调用摘要。"""

    model_config = ConfigDict(extra="forbid")
    tool_call_id: str
    tool_name: str
    status: str
    source_version: str | None = None
    error_code: str | None = None
