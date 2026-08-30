package com.hengpick.mall.memory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemoryRepository {
    Optional<SessionContext> findSession(String sessionId);

    void insertProposal(MemoryProposal proposal);

    Optional<MemoryProposal> findProposal(String proposalId, String userId);

    boolean decideProposal(MemoryProposal proposal, String expectedStatus);

    void insertPreference(UserPreference preference);

    List<UserPreference> findActivePreferences(String userId, Instant now);

    record SessionContext(String userId, String categoryId, String recipientKey) {}
}
