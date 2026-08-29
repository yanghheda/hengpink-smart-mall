package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.DecisionStreamAccessRepository;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 只返回用户当前版本对应的 Run。 */
@Repository
@Profile("database")
public class MyBatisDecisionStreamAccessRepository implements DecisionStreamAccessRepository {
    private final DecisionMapper mapper;

    public MyBatisDecisionStreamAccessRepository(DecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findCurrentRunId(String sessionId, String userId) {
        return Optional.ofNullable(mapper.findCurrentRunId(sessionId, userId));
    }
}
