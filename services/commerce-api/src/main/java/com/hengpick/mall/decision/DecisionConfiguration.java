package com.hengpick.mall.decision;

import com.hengpick.mall.decision.application.DecisionRunCoordinator;
import com.hengpick.mall.decision.application.DecisionRunService;
import com.hengpick.mall.decision.application.DecisionSessionQueryService;
import com.hengpick.mall.decision.application.DecisionStreamQueryService;
import com.hengpick.mall.decision.application.DecisionTraceService;
import com.hengpick.mall.decision.application.RecommendationCallbackReportPublisher;
import com.hengpick.mall.decision.domain.DecisionRunRepository;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshotRepository;
import com.hengpick.mall.decision.domain.DecisionStreamAccessRepository;
import com.hengpick.mall.decision.domain.DecisionTraceRepository;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.decision.event.DecisionEventPublisher;
import com.hengpick.mall.decision.event.DecisionStreamStore;
import com.hengpick.mall.decision.infrastructure.RedisDecisionStreamStore;
import com.hengpick.mall.decision.infrastructure.DecisionMapper;
import com.hengpick.mall.recommendation.application.RecommendationReportService;
import com.hengpick.mall.decision.web.DecisionSseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.integration.agent.AgentProtocolProperties;
import com.hengpick.mall.integration.agent.AsyncAgentRunLauncher;
import com.hengpick.mall.integration.agent.CallbackTokenCodec;
import com.hengpick.mall.observability.CommerceServiceProperties;
import com.hengpick.mall.shared.UlidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 组装 Decision Run 创建与异步 Agent 调度用例。 */
@Configuration
@Profile("database")
public class DecisionConfiguration {
    @Bean
    RecommendationCallbackReportPublisher recommendationCallbackReportPublisher(
            DecisionMapper mapper, RecommendationReportService reportService, ObjectMapper objectMapper) {
        return new RecommendationCallbackReportPublisher(mapper, reportService, objectMapper);
    }

    @Bean
    DecisionTraceService decisionTraceService(DecisionTraceRepository repository, ObjectAccessGuard accessGuard) {
        return new DecisionTraceService(repository, accessGuard);
    }
    @Bean
    DecisionStreamStore decisionStreamStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisDecisionStreamStore(redis, objectMapper);
    }

    @Bean
    DecisionEventPublisher decisionEventPublisher(DecisionStreamStore store, Clock clock) {
        return new DecisionEventPublisher(store, clock::instant);
    }

    @Bean
    DecisionSseService decisionSseService(DecisionStreamStore store, ObjectMapper objectMapper) {
        return new DecisionSseService(store, objectMapper);
    }

    @Bean
    DecisionStreamQueryService decisionStreamQueryService(DecisionStreamAccessRepository repository) {
        return new DecisionStreamQueryService(repository);
    }

    @Bean
    DecisionSessionQueryService decisionSessionQueryService(DecisionSessionSnapshotRepository repository) {
        return new DecisionSessionQueryService(repository);
    }

    @Bean
    DecisionRunService decisionRunService(
            DecisionRunRepository repository, UlidGenerator ulidGenerator, Clock clock) {
        return new DecisionRunService(repository, ulidGenerator::next, clock);
    }

    @Bean
    DecisionRunCoordinator decisionRunCoordinator(
            DecisionRunService runService,
            AsyncAgentRunLauncher launcher,
            CallbackTokenCodec tokenCodec,
            AgentProtocolProperties agentProperties,
            CommerceServiceProperties commerceProperties,
            Clock clock,
            DecisionEventPublisher eventPublisher) {
        return new DecisionRunCoordinator(runService, launcher, tokenCodec, agentProperties, clock,
                commerceProperties.datasetVersion(), eventPublisher);
    }
}
