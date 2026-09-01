package com.hengpick.mall.recommendation;

import com.hengpick.mall.recommendation.application.RecommendationReportService;
import com.hengpick.mall.recommendation.domain.RecommendationReportRepository;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 组装权威报告发布与确定性权重重算用例。 */
@Configuration
@Profile("database")
public class RecommendationConfiguration {
    @Bean
    RecommendationScorer recommendationScorer() {
        return new RecommendationScorer();
    }

    @Bean
    RecommendationReportService recommendationReportService(
            RecommendationReportRepository repository,
            RecommendationScorer scorer,
            Clock clock) {
        return new RecommendationReportService(repository, scorer, clock);
    }
}
