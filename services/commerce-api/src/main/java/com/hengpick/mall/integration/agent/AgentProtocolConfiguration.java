package com.hengpick.mall.integration.agent;

import com.hengpick.mall.decision.application.DecisionCallbackService;
import com.hengpick.mall.decision.event.DecisionEventPublisher;
import com.hengpick.mall.decision.domain.DecisionCallbackRepository;
import com.hengpick.mall.observability.CommerceServiceProperties;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 组装 P09-S02 的受控异步执行器、HTTP Client 与回调服务。 */
@Configuration
@Profile("database")
public class AgentProtocolConfiguration {
    @Bean
    CallbackTokenCodec callbackTokenCodec(AgentProtocolProperties properties) {
        return new JwtCallbackTokenCodec(properties.callbackSecret());
    }

    @Bean
    Executor agentRunExecutor(AgentProtocolProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("agent-run-");
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean
    AgentRunClient agentRunClient(CommerceServiceProperties properties) {
        return new RestAgentRunClient(properties.agentUrl());
    }

    @Bean
    AsyncAgentRunLauncher asyncAgentRunLauncher(Executor agentRunExecutor, AgentRunClient client) {
        return new AsyncAgentRunLauncher(agentRunExecutor, client);
    }

    @Bean
    DecisionCallbackService decisionCallbackService(
            DecisionCallbackRepository repository, DecisionEventPublisher eventPublisher) {
        return new DecisionCallbackService(repository, eventPublisher);
    }
}
