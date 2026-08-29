package com.hengpick.mall.integration.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.catalog.application.CatalogQueryService;
import com.hengpick.mall.catalog.application.CatalogSearchService;
import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.recommendation.domain.RecommendationScorer;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 内部 Commerce Tool 对既有领域服务的装配入口。 */
@Configuration
@Profile("database")
public class CommerceToolConfiguration {
    @Bean
    CommerceToolService commerceToolService(
            CatalogSearchService searchService,
            CatalogQueryService queryService,
            OfferQueryService offerQueryService,
            ObjectMapper objectMapper,
            Clock clock) {
        return new CommerceToolService(
                searchService, queryService, offerQueryService,
                new RecommendationScorer(), objectMapper, clock);
    }
}
