package com.hengpick.mall.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.application.CallbackConflictException;
import com.hengpick.mall.decision.application.RecommendationCallbackReportPublisher;
import com.hengpick.mall.decision.domain.AgentStepCallback;
import com.hengpick.mall.decision.domain.DecisionCallbackRepository;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import com.hengpick.mall.shared.UlidGenerator;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 使用数据库事务保证回调写入、Run 状态与 Session 当前状态一致。 */
@Repository
@Profile("database")
public class MyBatisDecisionCallbackRepository implements DecisionCallbackRepository {
    private final DecisionMapper mapper;
    private final ObjectMapper objectMapper;
    private final UlidGenerator ulidGenerator;
    private final RecommendationCallbackReportPublisher reportPublisher;

    public MyBatisDecisionCallbackRepository(
            DecisionMapper mapper, ObjectMapper objectMapper, UlidGenerator ulidGenerator) {
        this(mapper, objectMapper, ulidGenerator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MyBatisDecisionCallbackRepository(
            DecisionMapper mapper,
            ObjectMapper objectMapper,
            UlidGenerator ulidGenerator,
            RecommendationCallbackReportPublisher reportPublisher) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.ulidGenerator = ulidGenerator;
        this.reportPublisher = reportPublisher;
    }

    @Override
    public Optional<String> findSessionId(String runId) {
        return Optional.ofNullable(mapper.findSessionIdByRunId(runId));
    }

    @Override
    public Optional<String> findStepContentHash(String runId, int sequence) {
        return Optional.ofNullable(mapper.findStepContentHash(runId, sequence));
    }

    @Override
    public void appendStep(AgentStepCallback step) {
        var inserted = mapper.insertStep(ulidGenerator.next(), step, json(step.inputSummary()),
                json(step.outputSummary()));
        if (inserted != 1) {
            throw new CallbackConflictException("Run 不存在或回调版本不匹配");
        }
    }

    @Override
    public Optional<String> findCompletionContentHash(String runId) {
        return Optional.ofNullable(mapper.findCompletionContentHash(runId));
    }

    @Override
    @Transactional
    public boolean completeIfCurrent(RunCompletionCallback completion) {
        var sessionStatus = targetStatus(completion.completionType());
        if (mapper.completeSessionIfCurrent(completion.runId(), completion.runVersion(), sessionStatus,
                completion.completedAt()) != 1) {
            return false;
        }
        if (mapper.completeRun(completion.runId(), completion.runVersion(), sessionStatus,
                completion.completedAt()) != 1) {
            throw new CallbackConflictException("Run 已不是可完成状态");
        }
        mapper.insertRunResult(completion, json(completion.resultSummary()), true);
        if (reportPublisher != null) reportPublisher.publish(completion);
        return true;
    }

    @Override
    @Transactional
    public void markSuperseded(RunCompletionCallback completion) {
        mapper.completeRun(completion.runId(), completion.runVersion(), "SUPERSEDED", completion.completedAt());
        mapper.insertRunResult(completion, json(completion.resultSummary()), false);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("回调摘要无法序列化", exception);
        }
    }

    private String targetStatus(String completionType) {
        return switch (completionType) {
            case "REPORT_READY" -> "COMPLETED";
            case "CLARIFICATION_REQUIRED" -> "WAITING_CLARIFICATION";
            case "PARTIAL" -> "PARTIAL";
            case "FAILED", "NO_RESULT" -> "FAILED";
            default -> throw new CallbackConflictException("未知完成类型：" + completionType);
        };
    }
}
