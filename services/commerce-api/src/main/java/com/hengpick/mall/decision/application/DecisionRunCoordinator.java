package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.DecisionSession;
import com.hengpick.mall.decision.domain.RunTriggerType;
import com.hengpick.mall.integration.agent.AgentProtocolProperties;
import com.hengpick.mall.integration.agent.AgentRunRequest;
import com.hengpick.mall.integration.agent.AsyncAgentRunLauncher;
import com.hengpick.mall.integration.agent.CallbackTokenCodec;
import com.hengpick.mall.decision.event.DecisionEventPublisher;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在 Run 事务落库成功后签发短期凭证并异步提交 Python。 */
public final class DecisionRunCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionRunCoordinator.class);
    private final DecisionRunService runService;
    private final AsyncAgentRunLauncher launcher;
    private final CallbackTokenCodec tokenCodec;
    private final AgentProtocolProperties properties;
    private final Clock clock;
    private final String datasetVersion;
    private final DecisionEventPublisher eventPublisher;

    public DecisionRunCoordinator(
            DecisionRunService runService,
            AsyncAgentRunLauncher launcher,
            CallbackTokenCodec tokenCodec,
            AgentProtocolProperties properties,
            Clock clock,
            String datasetVersion) {
        this(runService, launcher, tokenCodec, properties, clock, datasetVersion, null);
    }

    public DecisionRunCoordinator(
            DecisionRunService runService,
            AsyncAgentRunLauncher launcher,
            CallbackTokenCodec tokenCodec,
            AgentProtocolProperties properties,
            Clock clock,
            String datasetVersion,
            DecisionEventPublisher eventPublisher) {
        this.runService = Objects.requireNonNull(runService);
        this.launcher = Objects.requireNonNull(launcher);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.datasetVersion = Objects.requireNonNull(datasetVersion);
        this.eventPublisher = eventPublisher;
    }

    public StartedDecisionRun startNextRun(DecisionSession session, RunTriggerType triggerType) {
        var started = runService.startNextRun(session, triggerType);
        var issuedAt = clock.instant();
        var callbackToken = tokenCodec.issue(started.run().id(), issuedAt,
                issuedAt.plus(properties.callbackTokenTtl()));
        launcher.launch(AgentRunRequest.stub(started.run().id(), started.session().id(),
                started.run().runVersion(), datasetVersion, callbackToken));
        if (eventPublisher != null) {
            try {
                eventPublisher.publishStarted(started.session().id(), started.run().id(), started.run().runVersion());
            } catch (RuntimeException exception) {
                LOGGER.warn("Redis 启动事件发布失败，Run 已按 MySQL 状态继续执行", exception);
            }
        }
        return started;
    }
}
