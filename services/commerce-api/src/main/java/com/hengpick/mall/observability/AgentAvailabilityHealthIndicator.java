package com.hengpick.mall.observability;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Agent availability is observable but never prevents deterministic commerce from serving. */
@Component("agentAvailability")
class AgentAvailabilityHealthIndicator implements HealthIndicator {

    private final CommerceServiceProperties properties;
    private final HttpClient httpClient;

    AgentAvailabilityHealthIndicator(CommerceServiceProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build();
    }

    @Override
    public Health health() {
        try {
            var request = HttpRequest.newBuilder(properties.agentUrl().resolve("/health/ready"))
                    .GET()
                    .timeout(Duration.ofMillis(500))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Health.up().withDetail("availability", "UP").build();
            }
        } catch (Exception ignored) {
            // The detailed reason stays in the Agent service; Commerce only exposes a safe degradation code.
        }
        return Health.up().withDetail("availability", "DEGRADED").withDetail("reason", "agent_unavailable").build();
    }
}
