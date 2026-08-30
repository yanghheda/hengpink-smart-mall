package com.hengpick.mall.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.memory.application.MemoryService;
import com.hengpick.mall.memory.application.MemorySnapshot;
import com.hengpick.mall.memory.domain.MemoryDecision;
import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.MemoryRepository;
import com.hengpick.mall.memory.domain.MemoryScope;
import com.hengpick.mall.memory.domain.PreferenceSource;
import com.hengpick.mall.memory.domain.UserPreference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private InMemoryRepository repository;
    private MemoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        var sequence = new AtomicInteger();
        service = new MemoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "01KTESTMEMORY" + String.format("%013d", sequence.incrementAndGet()));
    }

    @Test
    void narrowsGlobalProposalToRecipientContextForParentsAndDoesNotPersistBeforeConfirmation() {
        repository.session = Optional.of(new MemoryRepository.SessionContext("USER-1", "PHONE", "PARENTS"));

        var proposal = service.propose("USER-1", "SESSION-1", "BRAND", "PHONE.brand",
                Map.of("brand", "衡选"), "用户多次关注该品牌", MemoryScope.GLOBAL);

        assertThat(proposal.scope()).isEqualTo(MemoryScope.RECIPIENT_CONTEXT);
        assertThat(proposal.recipientKey()).isEqualTo("PARENTS");
        assertThat(repository.preferences).isEmpty();
    }

    @Test
    void acceptsProposalOnceAndExpiresPreferenceThirtyDaysAfterServerConfirmationTime() {
        repository.session = Optional.of(new MemoryRepository.SessionContext("USER-1", "PHONE", null));
        var proposal = service.propose("USER-1", "SESSION-1", "BRAND", "PHONE.brand",
                Map.of("brand", "衡选"), "用户明确表达品牌偏好", MemoryScope.CATEGORY);

        var result = service.decide("USER-1", proposal.id(), MemoryDecision.ACCEPT, null);

        assertThat(result.preference()).isPresent();
        assertThat(result.preference().orElseThrow().expiresAt()).isEqualTo(NOW.plusSeconds(30L * 24 * 60 * 60));
        assertThatThrownBy(() -> service.decide("USER-1", proposal.id(), MemoryDecision.REJECT, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    void modifyUsesUserValueWhileRejectNeverCreatesPreference() {
        repository.session = Optional.of(new MemoryRepository.SessionContext("USER-1", "PHONE", null));
        var modified = service.propose("USER-1", "SESSION-1", "BUDGET", "PHONE.budget",
                Map.of("max", 3000), "用户表达预算", MemoryScope.CATEGORY);
        var rejected = service.propose("USER-1", "SESSION-1", "BRAND", "PHONE.brand",
                Map.of("brand", "衡选"), "模型归纳", MemoryScope.CATEGORY);

        var modification = service.decide("USER-1", modified.id(), MemoryDecision.MODIFY, Map.of("max", 3500));
        var rejection = service.decide("USER-1", rejected.id(), MemoryDecision.REJECT, null);

        assertThat(modification.preference().orElseThrow().value()).containsEntry("max", 3500);
        assertThat(rejection.preference()).isEmpty();
        assertThat(repository.preferences).hasSize(1);
    }

    @Test
    void currentTaskOverridesLongTermPreferenceDeterministically() {
        var preference = new UserPreference("PREF-1", "USER-1", MemoryScope.CATEGORY, null, "PHONE",
                "BRAND", "PHONE.brand", Map.of("brand", "衡选"), "SESSION-OLD", NOW.minusSeconds(60),
                NOW.plusSeconds(3600));
        repository.preferences.add(preference);

        var resolved = service.resolve("USER-1", "PHONE", null,
                List.of(new MemorySnapshot("PHONE.brand", Map.of("brand", "不限"), PreferenceSource.CURRENT_TASK, null)));

        assertThat(resolved).singleElement().satisfies(value -> {
            assertThat(value.value()).containsEntry("brand", "不限");
            assertThat(value.source()).isEqualTo(PreferenceSource.CURRENT_TASK);
            assertThat(value.overriddenPreferenceId()).isEqualTo("PREF-1");
            assertThat(value.reason()).isEqualTo("MEMORY_OVERRIDDEN_BY_EXPLICIT_INPUT");
        });
    }

    private static final class InMemoryRepository implements MemoryRepository {
        private Optional<SessionContext> session = Optional.empty();
        private final List<MemoryProposal> proposals = new ArrayList<>();
        private final List<UserPreference> preferences = new ArrayList<>();

        @Override
        public Optional<SessionContext> findSession(String sessionId) {
            return session;
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
            if (!current.status().name().equals(expectedStatus)) {
                return false;
            }
            proposals.set(proposals.indexOf(current), proposal);
            return true;
        }

        @Override
        public void insertPreference(UserPreference preference) {
            preferences.add(preference);
        }

        @Override
        public List<UserPreference> findActivePreferences(String userId, Instant now) {
            return preferences.stream().filter(item -> item.userId().equals(userId) && item.expiresAt().isAfter(now)).toList();
        }
    }
}
