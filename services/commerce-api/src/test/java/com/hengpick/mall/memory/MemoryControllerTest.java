package com.hengpick.mall.memory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.identity.infrastructure.JwtH5SessionTokenIssuer;
import com.hengpick.mall.memory.application.MemoryService;
import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.MemoryProposalStatus;
import com.hengpick.mall.memory.domain.MemoryRepository;
import com.hengpick.mall.memory.domain.UserPreference;
import com.hengpick.mall.memory.web.MemoryController;
import com.hengpick.mall.memory.web.MemoryExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MemoryControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String SECRET = "memory-test-secret-must-be-at-least-32-bytes-long";
    private MockMvc mockMvc;
    private String authorization;

    @BeforeEach
    void setUp() {
        var repository = new InMemoryRepository();
        var sequence = new AtomicInteger();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service = new MemoryService(repository, clock,
                () -> "01KTESTMEMHTTP" + String.format("%012d", sequence.incrementAndGet()));
        var verifier = new JwtH5AccessTokenVerifier(SECRET, clock);
        mockMvc = MockMvcBuilders.standaloneSetup(new MemoryController(service, verifier, clock))
                .setControllerAdvice(new MemoryExceptionHandler()).build();
        authorization = "Bearer " + new JwtH5SessionTokenIssuer(SECRET)
                .issue("USER-1", "DEMO_USER", NOW.minusSeconds(60), NOW.plusSeconds(2 * 24 * 3600));
    }

    @Test
    void parentPurchaseCannotCreateGlobalPreferenceWithoutExplicitDecision() throws Exception {
        mockMvc.perform(post("/api/v1/decision-sessions/SESSION-1/memory-proposals")
                        .header("Authorization", authorization)
                        .contentType("application/json")
                        .content("""
                                {"proposalType":"BRAND","preferenceKey":"PHONE.brand",
                                 "value":{"brand":"衡选"},"rationaleSummary":"用户关注该品牌","scope":"GLOBAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("RECIPIENT_CONTEXT"))
                .andExpect(jsonPath("$.data.recipientKey").value("PARENTS"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/decision-sessions/SESSION-1/memory-proposals")
                        .contentType("application/json")
                        .content("""
                                {"proposalType":"BRAND","preferenceKey":"PHONE.brand",
                                 "value":{"brand":"衡选"},"rationaleSummary":"用户关注该品牌"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    private static final class InMemoryRepository implements MemoryRepository {
        private final List<MemoryProposal> proposals = new ArrayList<>();
        private final List<UserPreference> preferences = new ArrayList<>();

        @Override
        public Optional<SessionContext> findSession(String sessionId) {
            return Optional.of(new SessionContext("USER-1", "PHONE", "PARENTS"));
        }

        @Override
        public void insertProposal(MemoryProposal proposal) {
            proposals.add(proposal);
        }

        @Override
        public Optional<MemoryProposal> findProposal(String proposalId, String userId) {
            return proposals.stream().filter(item -> item.id().equals(proposalId) && item.userId().equals(userId)).findFirst();
        }

        @Override
        public boolean decideProposal(MemoryProposal proposal, String expectedStatus) {
            var current = findProposal(proposal.id(), proposal.userId()).orElseThrow();
            if (current.status() != MemoryProposalStatus.valueOf(expectedStatus)) return false;
            proposals.set(proposals.indexOf(current), proposal);
            return true;
        }

        @Override
        public void insertPreference(UserPreference preference) {
            preferences.add(preference);
        }

        @Override
        public List<UserPreference> findActivePreferences(String userId, Instant now) {
            return List.copyOf(preferences);
        }
    }
}
