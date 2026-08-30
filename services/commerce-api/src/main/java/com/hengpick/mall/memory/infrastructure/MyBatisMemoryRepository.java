package com.hengpick.mall.memory.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.MemoryProposalStatus;
import com.hengpick.mall.memory.domain.MemoryRepository;
import com.hengpick.mall.memory.domain.MemoryScope;
import com.hengpick.mall.memory.domain.UserPreference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class MyBatisMemoryRepository implements MemoryRepository {
    private final MemoryMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisMemoryRepository(MemoryMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SessionContext> findSession(String sessionId) {
        return Optional.ofNullable(mapper.findSession(sessionId));
    }

    @Override
    public void insertProposal(MemoryProposal proposal) {
        mapper.insertProposal(proposal, write(proposal.value()));
    }

    @Override
    public Optional<MemoryProposal> findProposal(String proposalId, String userId) {
        return Optional.ofNullable(mapper.findProposal(proposalId, userId)).map(this::proposal);
    }

    @Override
    public boolean decideProposal(MemoryProposal proposal, String expectedStatus) {
        return mapper.decideProposal(proposal, write(proposal.value()), expectedStatus) == 1;
    }

    @Override
    public void insertPreference(UserPreference preference) {
        mapper.insertPreference(preference, write(preference.value()));
    }

    @Override
    public List<UserPreference> findActivePreferences(String userId, Instant now) {
        return mapper.findActivePreferences(userId, now).stream().map(this::preference).toList();
    }

    private MemoryProposal proposal(MemoryProposalRow row) {
        return new MemoryProposal(row.id(), row.userId(), row.sessionId(), row.proposalType(), row.preferenceKey(),
                MemoryScope.valueOf(row.scope()), row.recipientKey(), row.categoryId(), read(row.valueJson()),
                row.rationaleSummary(), MemoryProposalStatus.valueOf(row.status()), row.expiresAt(), row.createdAt(),
                row.decidedAt());
    }

    private UserPreference preference(UserPreferenceRow row) {
        return new UserPreference(row.id(), row.userId(), MemoryScope.valueOf(row.scope()), row.recipientKey(),
                row.categoryId(), row.preferenceType(), row.preferenceKey(), read(row.valueJson()),
                row.sourceSessionId(), row.confirmedAt(), row.expiresAt());
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Memory value 无法序列化", exception);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Memory value 无法解析", exception);
        }
    }
}
