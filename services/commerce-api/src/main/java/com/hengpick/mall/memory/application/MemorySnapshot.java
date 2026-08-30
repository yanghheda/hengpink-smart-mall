package com.hengpick.mall.memory.application;

import com.hengpick.mall.memory.domain.PreferenceSource;
import java.util.Map;

public record MemorySnapshot(
        String preferenceKey,
        Map<String, Object> value,
        PreferenceSource source,
        String preferenceId) {}
