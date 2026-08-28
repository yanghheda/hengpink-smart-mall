package com.hengpick.mall.decision.domain;

import java.util.Map;

/** 集中保存详细设计 5.6 冻结的状态转换表。 */
public final class DecisionStateMachine {
    private static final Map<Transition, DecisionStatus> TRANSITIONS = Map.of(
            new Transition(DecisionStatus.DRAFT, DecisionEvent.RUN_REQUESTED), DecisionStatus.RUNNING,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.CLARIFICATION_REQUIRED),
                    DecisionStatus.WAITING_CLARIFICATION,
            new Transition(DecisionStatus.WAITING_CLARIFICATION, DecisionEvent.MESSAGE_APPENDED),
                    DecisionStatus.RUNNING,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.REPORT_VALIDATED), DecisionStatus.COMPLETED,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.DEGRADED_RESULT), DecisionStatus.PARTIAL,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.UNRECOVERABLE_ERROR), DecisionStatus.FAILED,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.NEWER_RUN_CREATED), DecisionStatus.SUPERSEDED,
            new Transition(DecisionStatus.RUNNING, DecisionEvent.USER_CANCELLED), DecisionStatus.CANCELLED);

    public DecisionStatus transition(DecisionStatus current, DecisionEvent event) {
        var next = TRANSITIONS.get(new Transition(current, event));
        if (next == null) {
            throw new InvalidDecisionTransitionException(
                    "不允许从 %s 通过 %s 转换状态".formatted(current, event));
        }
        return next;
    }

    public void requireClarificationQuestionCount(int questionCount) {
        if (questionCount < 1 || questionCount > 2) {
            throw new IllegalArgumentException("追问数量必须为 1 到 2 个");
        }
    }

    private record Transition(DecisionStatus current, DecisionEvent event) {}
}
