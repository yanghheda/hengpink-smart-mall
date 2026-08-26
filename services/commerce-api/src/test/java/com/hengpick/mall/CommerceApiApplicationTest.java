package com.hengpick.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
class CommerceApiApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock utcClock;

    @Test
    void applicationUsesAUTCClock() {
        assertThat(utcClock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void actuatorHealthIsVisible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessIsProcessOnlyAndReadinessKeepsCommerceAvailableWhenAgentIsDown()
            throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.agentAvailability.details.availability").value("DEGRADED"));
    }

    @Test
    void requestCorrelationAcceptsTrustedHeadersAndReturnsThem() throws Exception {
        var traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        mockMvc.perform(get("/")
                        .header("X-Request-Id", "01J00000000000000000000000")
                        .header("traceparent", traceparent))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "01J00000000000000000000000"))
                .andExpect(header().string("traceparent", traceparent));
    }

    @Test
    void landingPageDeclaresMinimalScope() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("commerce-api"))
                .andExpect(jsonPath("$.scope").value("P01-S01"));
    }
}
