package com.hengpick.mall.checkout;

import com.hengpick.mall.checkout.application.PurchaseIntentService;
import com.hengpick.mall.checkout.domain.CheckoutRepository;
import com.hengpick.mall.checkout.domain.CurrentPricePlanPort;
import com.hengpick.mall.shared.UlidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class CheckoutConfiguration {
    @Bean
    PurchaseIntentService purchaseIntentService(CheckoutRepository repository, CurrentPricePlanPort pricePort,
            Clock clock, UlidGenerator idGenerator) {
        return new PurchaseIntentService(repository, pricePort, clock, idGenerator::next);
    }
}
