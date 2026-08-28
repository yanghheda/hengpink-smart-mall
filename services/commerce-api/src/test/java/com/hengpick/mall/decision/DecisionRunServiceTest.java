package com.hengpick.mall.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hengpick.mall.decision.application.ActiveDecisionRunExistsException;
import com.hengpick.mall.decision.application.DecisionRunService;
import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionRunRepository;
import com.hengpick.mall.decision.domain.DecisionSession;
import com.hengpick.mall.decision.domain.DecisionStatus;
import com.hengpick.mall.decision.domain.ActiveRunConstraintViolationException;
import com.hengpick.mall.decision.domain.InvalidDecisionTransitionException;
import com.hengpick.mall.decision.domain.RunStatus;
import com.hengpick.mall.decision.domain.RunTriggerType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DecisionRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

    @Test
    void refusesASecondActiveRunForTheSameSession() {
        var repository = new InMemoryRepository();
        var service = service(repository);
        var session = session(DecisionStatus.RUNNING, 1);
        repository.runs.add(run("RUN-1", 1, RunStatus.RUNNING));

        assertThrows(ActiveDecisionRunExistsException.class,
                () -> service.startNextRun(session, RunTriggerType.USER_RETRY));
        assertEquals(1, repository.runs.size());
    }

    @Test
    void retriesATerminalRunByCreatingANewVersionWithoutChangingHistory() {
        var repository = new InMemoryRepository();
        var service = service(repository);
        var session = session(DecisionStatus.FAILED, 1);
        repository.runs.add(run("RUN-1", 1, RunStatus.FAILED));

        var result = service.startNextRun(session, RunTriggerType.USER_RETRY);

        assertEquals(2, result.session().currentRunVersion());
        assertEquals(DecisionStatus.RUNNING, result.session().status());
        assertEquals(2, result.run().runVersion());
        assertEquals(RunStatus.RUNNING, result.run().status());
        assertEquals(RunStatus.FAILED, repository.runs.getFirst().status());
    }

    @Test
    void convertsTheDatabaseConcurrencyGuardIntoADomainError() {
        var repository = new InMemoryRepository();
        repository.rejectNextCreate = true;
        var service = service(repository);

        assertThrows(ActiveDecisionRunExistsException.class,
                () -> service.startNextRun(session(DecisionStatus.FAILED, 1), RunTriggerType.USER_RETRY));
    }

    @Test
    void refusesRetryWhileThePreviousRunIsNotTerminal() {
        var service = service(new InMemoryRepository());

        assertThrows(InvalidDecisionTransitionException.class,
                () -> service.startNextRun(session(DecisionStatus.WAITING_CLARIFICATION, 1),
                        RunTriggerType.USER_RETRY));
    }

    private static DecisionRunService service(InMemoryRepository repository) {
        var sequence = new AtomicInteger();
        return new DecisionRunService(repository, () -> "RUN-" + sequence.incrementAndGet(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DecisionSession session(DecisionStatus status, int version) {
        return new DecisionSession("SESSION-1", "USER-1", status, version, 0, 0L);
    }

    private static DecisionRun run(String id, int version, RunStatus status) {
        return new DecisionRun(id, "SESSION-1", version, status, RunTriggerType.INITIAL_REQUEST, NOW, null);
    }

    private static final class InMemoryRepository implements DecisionRunRepository {
        private final List<DecisionRun> runs = new ArrayList<>();
        private boolean rejectNextCreate;

        @Override
        public boolean hasActiveRun(String sessionId) {
            return runs.stream().anyMatch(run -> run.sessionId().equals(sessionId) && run.status() == RunStatus.RUNNING);
        }

        @Override
        public void createRunAndAdvanceSession(DecisionRun run, DecisionSession session) {
            if (rejectNextCreate) {
                throw new ActiveRunConstraintViolationException("并发写入被数据库约束拒绝");
            }
            runs.add(run);
        }
    }
}
