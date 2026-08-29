package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.AgentStepCallback;
import com.hengpick.mall.decision.domain.DecisionCallbackRepository;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import com.hengpick.mall.decision.event.DecisionEventPublisher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 校验回调幂等语义，并把旧 Run 完成结果隔离为 SUPERSEDED。 */
public final class DecisionCallbackService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionCallbackService.class);
    private final DecisionCallbackRepository repository;
    private final DecisionEventPublisher eventPublisher;

    public DecisionCallbackService(DecisionCallbackRepository repository) {
        this(repository, null);
    }

    public DecisionCallbackService(DecisionCallbackRepository repository, DecisionEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = eventPublisher;
    }

    public void appendStep(AgentStepCallback step) {
        var existing = repository.findStepContentHash(step.runId(), step.sequence());
        if (existing.isPresent()) {
            requireSameHash(existing.get(), step.contentHash(), "Step");
            return;
        }
        repository.appendStep(step);
        publishStep(step);
    }

    public RunCompletionOutcome complete(RunCompletionCallback completion) {
        var existing = repository.findCompletionContentHash(completion.runId());
        if (existing.isPresent()) {
            requireSameHash(existing.get(), completion.contentHash(), "完成回调");
            return RunCompletionOutcome.IDEMPOTENT;
        }
        if (repository.completeIfCurrent(completion)) {
            publishCompletion(completion);
            return RunCompletionOutcome.APPLIED;
        }
        repository.markSuperseded(completion);
        return RunCompletionOutcome.SUPERSEDED;
    }

    private void publishStep(AgentStepCallback step) {
        if (eventPublisher == null) return;
        repository.findSessionId(step.runId()).ifPresent(sessionId -> safelyPublish(() -> eventPublisher.publishStage(
                sessionId, step.runId(), step.runVersion(), step.node(), step.status(), progress(step.node()),
                displayText(step.node()), "step:" + step.sequence())));
    }

    private void publishCompletion(RunCompletionCallback completion) {
        if (eventPublisher == null || !"REPORT_READY".equals(completion.completionType())) return;
        repository.findSessionId(completion.runId()).ifPresent(sessionId -> safelyPublish(() ->
                eventPublisher.publishCompleted(sessionId, completion.runId(), completion.runVersion(),
                        "completion:" + completion.contentHash())));
    }

    private void safelyPublish(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis 进度事件发布失败，MySQL 业务结果保持有效", exception);
        }
    }

    private int progress(String node) {
        return switch (node) {
            case "LOAD" -> 5;
            case "INTENT" -> 20;
            case "PRODUCT", "PRODUCT_SEARCH" -> 40;
            case "REVIEW", "PRICE", "STUB" -> 65;
            case "SCORE" -> 80;
            case "REPORT" -> 92;
            case "VALIDATE" -> 98;
            default -> 5;
        };
    }

    private String displayText(String node) {
        return switch (node) {
            case "INTENT" -> "已理解购买需求";
            case "PRODUCT", "PRODUCT_SEARCH" -> "商品候选筛选完成";
            case "REVIEW" -> "商品评价证据分析完成";
            case "PRICE" -> "价格方案计算完成";
            case "SCORE" -> "候选排序完成";
            case "REPORT", "VALIDATE" -> "正在校验购买建议";
            case "STUB" -> "Stub 分析阶段完成";
            default -> "决策分析进行中";
        };
    }

    private void requireSameHash(String existing, String incoming, String callbackType) {
        if (!existing.equals(incoming)) {
            throw new CallbackConflictException(callbackType + " 幂等键对应的内容哈希冲突");
        }
    }
}
