package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.DecisionSessionSnapshot;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshotRepository;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 从业务表读取可恢复快照，Redis 清空不影响查询。 */
@Repository
@Profile("database")
public class MyBatisDecisionSessionSnapshotRepository implements DecisionSessionSnapshotRepository {
    private final DecisionMapper mapper;

    public MyBatisDecisionSessionSnapshotRepository(DecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DecisionSessionSnapshot> findSnapshot(String sessionId, String userId) {
        return Optional.ofNullable(mapper.findSessionSnapshot(sessionId, userId));
    }
}
