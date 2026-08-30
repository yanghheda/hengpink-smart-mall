package com.hengpick.mall.memory.application;

import com.hengpick.mall.memory.domain.MemoryDecision;
import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.MemoryProposalStatus;
import com.hengpick.mall.memory.domain.MemoryRepository;
import com.hengpick.mall.memory.domain.MemoryScope;
import com.hengpick.mall.memory.domain.PreferenceSource;
import com.hengpick.mall.memory.domain.UserPreference;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;

public class MemoryService {
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final Duration PROPOSAL_RETENTION = Duration.ofDays(7);
    private final MemoryRepository repository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public MemoryService(MemoryRepository repository, Clock clock, Supplier<String> idGenerator) {
        this.repository = repository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public MemoryProposal propose(
            String userId,
            String sessionId,
            String proposalType,
            String preferenceKey,
            Map<String, Object> value,
            String rationaleSummary,
            MemoryScope requestedScope) {
        var session = repository.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision Session 不存在"));
        if (!session.userId().equals(userId)) {
            throw new IllegalArgumentException("Decision Session 不属于当前用户");
        }
        requireText(proposalType, "proposalType");
        requireText(preferenceKey, "preferenceKey");
        requireText(rationaleSummary, "rationaleSummary");
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value 不能为空");
        }
        var scope = requestedScope == null ? defaultScope(session) : requestedScope;
        if (session.recipientKey() != null && !session.recipientKey().isBlank()) {
            scope = MemoryScope.RECIPIENT_CONTEXT;
        }
        var now = clock.instant();
        var proposal = new MemoryProposal(idGenerator.get(), userId, sessionId, proposalType, preferenceKey,
                scope, scope == MemoryScope.RECIPIENT_CONTEXT ? session.recipientKey() : null,
                scope == MemoryScope.GLOBAL ? null : session.categoryId(), Map.copyOf(value), rationaleSummary,
                MemoryProposalStatus.PENDING, now.plus(PROPOSAL_RETENTION), now, null);
        repository.insertProposal(proposal);
        return proposal;
    }

    @Transactional
    public MemoryDecisionResult decide(
            String userId, String proposalId, MemoryDecision decision, Map<String, Object> modifiedValue) {
        var proposal = repository.findProposal(proposalId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Memory Proposal 不存在"));
        var now = clock.instant();
        if (proposal.status() != MemoryProposalStatus.PENDING || !proposal.expiresAt().isAfter(now)) {
            throw new IllegalStateException("Memory Proposal 已处理或已过期");
        }
        if (decision == MemoryDecision.MODIFY && (modifiedValue == null || modifiedValue.isEmpty())) {
            throw new IllegalArgumentException("修改时必须提供 value");
        }
        var status = switch (decision) {
            case ACCEPT -> MemoryProposalStatus.ACCEPTED;
            case MODIFY -> MemoryProposalStatus.MODIFIED;
            case REJECT -> MemoryProposalStatus.REJECTED;
        };
        var value = decision == MemoryDecision.MODIFY ? Map.copyOf(modifiedValue) : proposal.value();
        var decided = new MemoryProposal(proposal.id(), proposal.userId(), proposal.sessionId(), proposal.proposalType(),
                proposal.preferenceKey(), proposal.scope(), proposal.recipientKey(), proposal.categoryId(), value,
                proposal.rationaleSummary(), status, proposal.expiresAt(), proposal.createdAt(), now);
        if (!repository.decideProposal(decided, MemoryProposalStatus.PENDING.name())) {
            throw new IllegalStateException("Memory Proposal 已处理");
        }
        if (decision == MemoryDecision.REJECT) {
            return new MemoryDecisionResult(decided, java.util.Optional.empty());
        }
        var preference = new UserPreference(idGenerator.get(), proposal.userId(), proposal.scope(),
                proposal.recipientKey(), proposal.categoryId(), proposal.proposalType(), proposal.preferenceKey(), value,
                proposal.sessionId(), now, now.plus(DEFAULT_RETENTION));
        repository.insertPreference(preference);
        return new MemoryDecisionResult(decided, java.util.Optional.of(preference));
    }

    public List<ResolvedPreference> resolve(
            String userId, String categoryId, String recipientKey, List<MemorySnapshot> currentContext) {
        var candidates = new ArrayList<MemorySnapshot>();
        for (var preference : repository.findActivePreferences(userId, clock.instant())) {
            if (applies(preference, categoryId, recipientKey)) {
                candidates.add(new MemorySnapshot(preference.preferenceKey(), preference.value(),
                        source(preference.scope()), preference.id()));
            }
        }
        if (currentContext != null) {
            candidates.addAll(currentContext);
        }
        candidates.sort(Comparator.comparingInt((MemorySnapshot item) -> item.source().ordinal()));
        var resolved = new LinkedHashMap<String, ResolvedPreference>();
        for (var candidate : candidates) {
            var previous = resolved.get(candidate.preferenceKey());
            resolved.put(candidate.preferenceKey(), new ResolvedPreference(candidate.preferenceKey(), candidate.value(),
                    candidate.source(), candidate.preferenceId(), previous == null ? null : previous.preferenceId(),
                    previous == null ? null : overrideReason(candidate.source())));
        }
        return List.copyOf(resolved.values());
    }

    private boolean applies(UserPreference preference, String categoryId, String recipientKey) {
        return switch (preference.scope()) {
            case GLOBAL -> true;
            case CATEGORY -> Objects.equals(preference.categoryId(), categoryId);
            case RECIPIENT_CONTEXT -> Objects.equals(preference.categoryId(), categoryId)
                    && Objects.equals(preference.recipientKey(), recipientKey);
        };
    }

    private PreferenceSource source(MemoryScope scope) {
        return switch (scope) {
            case GLOBAL -> PreferenceSource.GLOBAL;
            case CATEGORY, RECIPIENT_CONTEXT -> PreferenceSource.CATEGORY;
        };
    }

    private String overrideReason(PreferenceSource source) {
        return source == PreferenceSource.CURRENT_TASK
                ? "MEMORY_OVERRIDDEN_BY_EXPLICIT_INPUT"
                : "MEMORY_OVERRIDDEN_BY_HIGHER_PRIORITY_CONTEXT";
    }

    private MemoryScope defaultScope(MemoryRepository.SessionContext session) {
        return session.recipientKey() == null || session.recipientKey().isBlank()
                ? MemoryScope.CATEGORY
                : MemoryScope.RECIPIENT_CONTEXT;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
