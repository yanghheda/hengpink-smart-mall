package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hengpick.mall.identity.domain.SmartMallTicket;
import com.hengpick.mall.identity.domain.SmartMallTicketRepository;
import com.hengpick.mall.identity.infrastructure.RedisSmartMallTicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "VM_REDIS_INTEGRATION", matches = "true")
class RedisSmartMallTicketRepositoryIntegrationTest {
    @Test
    void luaAllowsOnlyOneConsumerAndPreservesTicketAfterBindingMismatch() throws Exception {
        var configuration = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("VM_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("VM_REDIS_PORT", "16379")));
        configuration.setPassword(RedisPassword.of(System.getenv("REDIS_PASSWORD")));
        var connectionFactory = new LettuceConnectionFactory(configuration);
        try {
            connectionFactory.afterPropertiesSet();
            var template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var repository = new RedisSmartMallTicketRepository(template, objectMapper);
            var hash = "integration-" + java.util.UUID.randomUUID();
            var ticket = new SmartMallTicket(hash, "USER-1", "DEMO_USER", "REACT_NATIVE", "device-1",
                    "https://smart.example", Instant.now().plusSeconds(300));

            assertThat(repository.save(ticket, Duration.ofMinutes(5))).isTrue();
            assertThat(repository.consume(hash, "REACT_NATIVE", "wrong-device", "https://smart.example",
                    Instant.now()).status()).isEqualTo(SmartMallTicketRepository.Status.INVALID);

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> consume(repository, ticket, ready, start));
                var second = executor.submit(() -> consume(repository, ticket, ready, start));
                ready.await();
                start.countDown();
                assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(
                        SmartMallTicketRepository.Status.CONSUMED, SmartMallTicketRepository.Status.INVALID);
            }

            var expiredHash = "integration-expired-" + java.util.UUID.randomUUID();
            var expiringTicket = new SmartMallTicket(expiredHash, "USER-1", "DEMO_USER", "REACT_NATIVE",
                    "device-1", "https://smart.example", Instant.now().plusMillis(100));
            assertThat(repository.save(expiringTicket, Duration.ofMillis(100))).isTrue();
            Thread.sleep(120);
            assertThat(repository.consume(expiredHash, "REACT_NATIVE", "device-1", "https://smart.example",
                    Instant.now()).status()).isEqualTo(SmartMallTicketRepository.Status.EXPIRED);
        } finally {
            connectionFactory.destroy();
        }
    }

    private SmartMallTicketRepository.Status consume(RedisSmartMallTicketRepository repository,
            SmartMallTicket ticket, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return repository.consume(ticket.ticketHash(), ticket.hostType(), ticket.deviceSessionId(),
                    ticket.h5Origin(), Instant.now()).status();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redis 并发测试被中断", exception);
        }
    }
}
