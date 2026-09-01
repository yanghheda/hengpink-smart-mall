package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.ActiveRunConstraintViolationException;
import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionRunRepository;
import com.hengpick.mall.decision.domain.DecisionSession;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 使用单个数据库事务推进 Session 并创建不可覆写的 Run。 */
@Repository
@Profile("database")
public class MyBatisDecisionRunRepository implements DecisionRunRepository {
    private final DecisionMapper mapper;

    public MyBatisDecisionRunRepository(DecisionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void createInitialRun(
            DecisionSession session,
            DecisionRun run,
            String title,
            String intentJson,
            String weightsJson,
            String datasetVersion,
            String categorySchemaVersion,
            String messageId,
            String messageContent) {
        try {
            mapper.insertInitialSession(session, session.status().name(), title, intentJson, weightsJson,
                    datasetVersion, categorySchemaVersion, run.startedAt());
            mapper.insertInitialMessage(messageId, session.id(), messageContent, run.startedAt());
            mapper.insertRun(run);
        } catch (DataIntegrityViolationException exception) {
            throw new ActiveRunConstraintViolationException("首次决策 Run 创建冲突", exception);
        }
    }

    @Override
    public boolean hasActiveRun(String sessionId) {
        return mapper.hasActiveRun(sessionId);
    }

    @Override
    public Optional<DecisionSession> findOwnedSession(String sessionId, String userId) {
        return Optional.ofNullable(mapper.findOwnedSession(sessionId, userId));
    }

    @Override
    public List<String> findUserMessages(String sessionId) {
        return mapper.findUserMessages(sessionId);
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

    @Override
    @Transactional
    public void createRunWithMessageAndAdvanceSession(
            DecisionRun run, DecisionSession session, String messageId, String content, Instant createdAt) {
        createRunAndAdvanceSession(run, session);
        mapper.insertUserMessage(messageId, session.id(), run.runVersion(), content, createdAt);
    }
}
