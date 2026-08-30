from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class CandidateSlot(StrEnum):
    PRIMARY = "PRIMARY"
    ALTERNATIVE_1 = "ALTERNATIVE_1"
    ALTERNATIVE_2 = "ALTERNATIVE_2"


class ReportReason(BaseModel):
    """模型只描述理由并引用已有事实，不承载确定性数值。"""

    model_config = ConfigDict(extra="forbid")
    text: str = Field(min_length=1, max_length=300)
    fact_ids: list[str] = Field(default_factory=list, max_length=10)
    evidence_ids: list[str] = Field(default_factory=list, max_length=10)

    @model_validator(mode="after")
    def require_citation(self) -> "ReportReason":
        if not self.fact_ids and not self.evidence_ids:
            raise ValueError("每条推荐理由必须引用 Fact 或 Evidence")
        return self


class CandidateNarrative(BaseModel):
    """候选槽位由确定性排名映射，模型不得回写候选身份。"""

    model_config = ConfigDict(extra="forbid")
    candidate_slot: CandidateSlot
    reasons: list[ReportReason] = Field(min_length=1, max_length=5)
    risks: list[str] = Field(default_factory=list, max_length=5)
    data_gaps: list[str] = Field(default_factory=list, max_length=5)


class RejectedPopularCandidateNarrative(BaseModel):
    model_config = ConfigDict(extra="forbid")
    label: str = Field(min_length=1, max_length=80)
    reason: str = Field(min_length=1, max_length=300)


class DecisionReportNarrative(BaseModel):
    """模型输出 Schema；有意不包含金额、评分、排名和业务对象 ID。"""

    model_config = ConfigDict(extra="forbid")
    summary: str = Field(min_length=1, max_length=500)
    recommendations: list[CandidateNarrative] = Field(min_length=1, max_length=3)
    rejected_popular_candidates: list[RejectedPopularCandidateNarrative] = Field(
        default_factory=list, max_length=5
    )
    counterfactuals: list[str] = Field(default_factory=list, max_length=5)
    overall_data_gaps: list[str] = Field(default_factory=list, max_length=5)

    @model_validator(mode="after")
    def validate_slots(self) -> "DecisionReportNarrative":
        slots = [item.candidate_slot for item in self.recommendations]
        if not slots or slots[0] != CandidateSlot.PRIMARY:
            raise ValueError("报告第一项必须是 PRIMARY")
        if len(slots) != len(set(slots)):
            raise ValueError("候选槽位不能重复")
        expected = list(CandidateSlot)[: len(slots)]
        if slots != expected:
            raise ValueError("候选槽位必须按确定性排名连续排列")
        return self
