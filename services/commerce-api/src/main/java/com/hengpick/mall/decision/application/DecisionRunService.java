package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.ActiveRunConstraintViolationException;
import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionRunRepository;
import com.hengpick.mall.decision.domain.DecisionSession;
import com.hengpick.mall.decision.domain.DecisionStatus;
import com.hengpick.mall.decision.domain.InvalidDecisionTransitionException;
import com.hengpick.mall.decision.domain.RunStatus;
import com.hengpick.mall.decision.domain.RunTriggerType;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** 编排新 Run 版本创建，并把数据库竞态转换为稳定领域错误。 */
public final class DecisionRunService {
    private final DecisionRunRepository repository;
    private final Supplier<String> idGenerator;
    private final Clock clock;

    public DecisionRunService(DecisionRunRepository repository, Supplier<String> idGenerator, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public StartedDecisionRun startNextRun(DecisionSession currentSession, RunTriggerType triggerType) {
        if (repository.hasActiveRun(currentSession.id())) {
            throw activeRunExists();
        }
        requireAllowedTrigger(currentSession, triggerType);
        var nextVersion = Math.addExact(currentSession.currentRunVersion(), 1);
        var nextSession = currentSession.advanceToRunningRun(nextVersion);
        var run = new DecisionRun(idGenerator.get(), currentSession.id(), nextVersion, RunStatus.RUNNING,
                triggerType, clock.instant(), null);
        try {
            repository.createRunAndAdvanceSession(run, nextSession);
        } catch (ActiveRunConstraintViolationException exception) {
            throw new ActiveDecisionRunExistsException("同一决策会话已有活跃 Run", exception);
        }
        return new StartedDecisionRun(nextSession, run);
    }

    private ActiveDecisionRunExistsException activeRunExists() {
        return new ActiveDecisionRunExistsException("同一决策会话已有活跃 Run");
    }

    private void requireAllowedTrigger(DecisionSession session, RunTriggerType triggerType) {
        var allowed = switch (triggerType) {
            case INITIAL_REQUEST -> session.status() == DecisionStatus.DRAFT && session.currentRunVersion() == 0;
            case MESSAGE_APPENDED -> session.status() == DecisionStatus.WAITING_CLARIFICATION;
            case USER_RETRY -> switch (session.status()) {
                case COMPLETED, PARTIAL, FAILED, SUPERSEDED, CANCELLED -> true;
                default -> false;
            };
        };
        if (!allowed) {
            throw new InvalidDecisionTransitionException(
                    "状态 %s 不允许通过 %s 创建新 Run".formatted(session.status(), triggerType));
        }
    }
}
