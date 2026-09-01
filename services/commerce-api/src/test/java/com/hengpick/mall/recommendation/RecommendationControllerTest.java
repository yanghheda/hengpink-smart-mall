package com.hengpick.mall.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.identity.infrastructure.JwtH5SessionTokenIssuer;
import com.hengpick.mall.recommendation.application.RecommendationReweightUseCase;
import com.hengpick.mall.recommendation.application.ReweightResult;
import com.hengpick.mall.recommendation.domain.Dimension;
import com.hengpick.mall.recommendation.domain.ReportVersionConflictException;
import com.hengpick.mall.recommendation.web.RecommendationController;
import com.hengpick.mall.recommendation.web.RecommendationExceptionHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");
    private static final String SECRET = "recommendation-test-secret-32-bytes";
    private FakeUseCase service;
    private MockMvc mockMvc;
    private String authorization;

    @BeforeEach
    void setUp() {
        service = new FakeUseCase();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationController(
                        service, new JwtH5AccessTokenVerifier(SECRET, clock), clock))
                .setControllerAdvice(new RecommendationExceptionHandler()).build();
        authorization = "Bearer " + new JwtH5SessionTokenIssuer(SECRET)
                .issue("USER-1", "DEMO_USER", NOW.minusSeconds(1), NOW.plusSeconds(3600));
    }

    @Test
    void validRequestReturnsNewVersionAndExplicitGenerationType() throws Exception {
        service.result = new ReweightResult(
                "SESSION-1", 2, "SKU-1", Map.of(Dimension.NEED_MATCH, BigDecimal.ONE),
                List.of(new ReweightResult.RankedCandidate(
                        1, "PRODUCT-1", "SKU-1", new BigDecimal("90"), "PLAN-1", "2999.00")),
                "DETERMINISTIC_REWEIGHT");

        mockMvc.perform(put("/api/v1/decision-sessions/SESSION-1/weights")
                        .header("Authorization", authorization).contentType("application/json")
                        .content("{\"reportVersion\":1,\"weights\":{\"NEED_MATCH\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.generationType").value("DETERMINISTIC_REWEIGHT"));
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        service.failure = new ReportVersionConflictException(1, 2);

        mockMvc.perform(put("/api/v1/decision-sessions/SESSION-1/weights")
                        .header("Authorization", authorization).contentType("application/json")
                        .content("{\"reportVersion\":1,\"weights\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_VERSION_CONFLICT"));
    }

    @Test
    void invalidTokenNeverReachesReweightService() throws Exception {
        mockMvc.perform(put("/api/v1/decision-sessions/SESSION-1/weights")
                        .header("Authorization", "Bearer invalid").contentType("application/json")
                        .content("{\"reportVersion\":1,\"weights\":{}}"))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(service.called).isFalse();
    }

    private static final class FakeUseCase implements RecommendationReweightUseCase {
        private ReweightResult result;
        private RuntimeException failure;
        private boolean called;

        @Override
        public ReweightResult reweight(
                String userId,
                String sessionId,
                int expectedReportVersion,
                Map<Dimension, BigDecimal> requestedWeights) {
            called = true;
            if (failure != null) throw failure;
            return result;
        }
    }
}
