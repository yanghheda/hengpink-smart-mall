package com.hengpick.mall.memory.application;

import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.UserPreference;
import java.util.Optional;

public record MemoryDecisionResult(MemoryProposal proposal, Optional<UserPreference> preference) {}
