package com.hengpick.mall.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hengpick.mall.decision.domain.DecisionEvent;
import com.hengpick.mall.decision.domain.DecisionStateMachine;
import com.hengpick.mall.decision.domain.DecisionStatus;
import com.hengpick.mall.decision.domain.InvalidDecisionTransitionException;
import org.junit.jupiter.api.Test;

class DecisionStateMachineTest {

    private final DecisionStateMachine stateMachine = new DecisionStateMachine();

    @Test
    void followsTheFrozenDecisionTransitionTable() {
        assertEquals(DecisionStatus.RUNNING,
                stateMachine.transition(DecisionStatus.DRAFT, DecisionEvent.RUN_REQUESTED));
        assertEquals(DecisionStatus.WAITING_CLARIFICATION,
                stateMachine.transition(DecisionStatus.RUNNING, DecisionEvent.CLARIFICATION_REQUIRED));
        assertEquals(DecisionStatus.RUNNING,
                stateMachine.transition(DecisionStatus.WAITING_CLARIFICATION, DecisionEvent.MESSAGE_APPENDED));
        assertEquals(DecisionStatus.COMPLETED,
                stateMachine.transition(DecisionStatus.RUNNING, DecisionEvent.REPORT_VALIDATED));
    }

    @Test
    void rejectsAnEventThatIsNotAllowedFromTheCurrentState() {
        var error = assertThrows(InvalidDecisionTransitionException.class,
                () -> stateMachine.transition(DecisionStatus.DRAFT, DecisionEvent.REPORT_VALIDATED));

        assertEquals("不允许从 DRAFT 通过 REPORT_VALIDATED 转换状态", error.getMessage());
    }

    @Test
    void rejectsMutatingACompletedRunInPlace() {
        assertThrows(InvalidDecisionTransitionException.class,
                () -> stateMachine.transition(DecisionStatus.COMPLETED, DecisionEvent.RUN_REQUESTED));
    }

    @Test
    void clarificationMustContainOneOrTwoQuestions() {
        assertThrows(IllegalArgumentException.class,
                () -> stateMachine.requireClarificationQuestionCount(0));
        assertThrows(IllegalArgumentException.class,
                () -> stateMachine.requireClarificationQuestionCount(3));
        stateMachine.requireClarificationQuestionCount(1);
        stateMachine.requireClarificationQuestionCount(2);
    }
}
