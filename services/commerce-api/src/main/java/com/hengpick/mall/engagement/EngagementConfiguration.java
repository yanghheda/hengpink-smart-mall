package com.hengpick.mall.engagement;

import com.hengpick.mall.engagement.application.EngagementService;
import com.hengpick.mall.engagement.domain.EngagementRepository;
import com.hengpick.mall.shared.UlidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class EngagementConfiguration {
    @Bean
    EngagementService engagementService(EngagementRepository repository, Clock clock, UlidGenerator idGenerator) {
        return new EngagementService(repository, clock, idGenerator::next);
    }
}
