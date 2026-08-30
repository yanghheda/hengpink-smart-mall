package com.hengpick.mall.memory;

import com.hengpick.mall.memory.application.MemoryService;
import com.hengpick.mall.memory.domain.MemoryRepository;
import com.hengpick.mall.shared.UlidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class MemoryConfiguration {
    @Bean
    MemoryService memoryService(MemoryRepository repository, Clock clock, UlidGenerator idGenerator) {
        return new MemoryService(repository, clock, idGenerator::next);
    }
}
