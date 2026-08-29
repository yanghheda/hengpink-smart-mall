package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.decision.application.DecisionSessionQueryService;
import com.hengpick.mall.decision.application.DecisionStreamNotFoundException;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshot;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecisionSessionQueryServiceTest {
    @Test
    void returnsMysqlSnapshotForOwnerEvenWhenStreamDoesNotExist() {
        var snapshot = new DecisionSessionSnapshot("session-1", "run-1", 1, "COMPLETED", 1);
        var service = new DecisionSessionQueryService((sessionId, userId) -> Optional.of(snapshot));

        assertThat(service.requireSnapshot("session-1", "user-1")).isEqualTo(snapshot);
    }

    @Test
    void doesNotRevealAnotherUsersSession() {
        DecisionSessionSnapshotRepository repository = (sessionId, userId) -> Optional.empty();
        var service = new DecisionSessionQueryService(repository);

        assertThatThrownBy(() -> service.requireSnapshot("session-1", "other-user"))
                .isInstanceOf(DecisionStreamNotFoundException.class)
                .hasMessage("决策会话不存在或无权访问");
    }
}
