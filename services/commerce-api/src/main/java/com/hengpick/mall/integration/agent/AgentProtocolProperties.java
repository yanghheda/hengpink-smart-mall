package com.hengpick.mall.integration.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Java 与 Python 内部 Run 协议的安全及执行器配置。 */
@Validated
@ConfigurationProperties("hengpick.agent-protocol")
public record AgentProtocolProperties(
        @NotBlank String callbackSecret,
        @NotNull Duration callbackTokenTtl,
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity) {}
