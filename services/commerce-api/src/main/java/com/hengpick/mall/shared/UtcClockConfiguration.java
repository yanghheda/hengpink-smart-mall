package com.hengpick.mall.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtcClockConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    UlidGenerator ulidGenerator(Clock utcClock) {
        return new UlidGenerator(utcClock);
    }
}
