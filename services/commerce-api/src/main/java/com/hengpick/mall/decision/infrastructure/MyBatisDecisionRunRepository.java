package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.ActiveRunConstraintViolationException;
import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionRunRepository;
import com.hengpick.mall.decision.domain.DecisionSession;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 使用单个数据库事务推进 Session 并创建不可覆写的 Run。 */
@Repository
@Profile("database")
public class MyBatisDecisionRunRepository implements DecisionRunRepository {
    private final DecisionMapper mapper;

    public MyBatisDecisionRunRepository(DecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean hasActiveRun(String sessionId) {
        return mapper.hasActiveRun(sessionId);
    }

    @Override
    @Transactional
    public void createRunAndAdvanceSession(DecisionRun run, DecisionSession session) {
        try {
            mapper.insertRun(run);
            var updated = mapper.advanceSession(session, session.status().name(), session.currentRunVersion(),
                    run.startedAt(), session.version(), session.currentRunVersion() - 1, session.version() - 1);
            if (updated != 1) {
                throw new ActiveRunConstraintViolationException("决策会话版本已被并发修改");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ActiveRunConstraintViolationException("活跃 Run 或 Run 版本唯一约束冲突", exception);
        }
    }
}
