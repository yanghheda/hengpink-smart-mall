package com.hengpick.mall.pricing.web;

import com.hengpick.mall.pricing.application.OfferQueryService;
import com.hengpick.mall.pricing.domain.OfferQueryPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Pricing 查询用例的依赖装配。 */
@Configuration
@Profile("database")
public class PricingConfiguration {

    @Bean
    OfferQueryService offerQueryService(OfferQueryPort queryPort, Clock clock) {
        return new OfferQueryService(queryPort, clock);
    }
}
