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

    public StartedDecisionRun startClarificationRun(DecisionSession currentSession, String answer) {
        if (repository.hasActiveRun(currentSession.id())) throw activeRunExists();
        requireAllowedTrigger(currentSession, RunTriggerType.MESSAGE_APPENDED);
        var nextVersion = Math.addExact(currentSession.currentRunVersion(), 1);
        var nextSession = currentSession.advanceToRunningRun(nextVersion);
        var now = clock.instant();
        var run = new DecisionRun(idGenerator.get(), currentSession.id(), nextVersion, RunStatus.RUNNING,
                RunTriggerType.MESSAGE_APPENDED, now, null);
        repository.createRunWithMessageAndAdvanceSession(run, nextSession, idGenerator.get(), answer, now);
        return new StartedDecisionRun(nextSession, run);
    }

    public DecisionSession requireOwnedSession(String sessionId, String userId) {
        return repository.findOwnedSession(sessionId, userId)
                .orElseThrow(() -> new DecisionStreamNotFoundException("决策会话不存在或无权访问"));
    }

    public java.util.List<String> userMessages(String sessionId) {
        return repository.findUserMessages(sessionId);
    }

    public StartedDecisionRun startInitialRun(
            String userId, String requirement, String datasetVersion, String categorySchemaVersion) {
        var now = clock.instant();
        var session = new DecisionSession(idGenerator.get(), userId, DecisionStatus.RUNNING, 1, 0, 1);
        var run = new DecisionRun(idGenerator.get(), session.id(), 1, RunStatus.RUNNING,
                RunTriggerType.INITIAL_REQUEST, now, null);
        var title = requirement.length() <= 80 ? requirement : requirement.substring(0, 80);
        repository.createInitialRun(session, run, title,
                "{\"requirement\":" + jsonString(requirement) + "}", "{}", datasetVersion,
                categorySchemaVersion, idGenerator.get(), requirement);
        return new StartedDecisionRun(session, run);
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
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
