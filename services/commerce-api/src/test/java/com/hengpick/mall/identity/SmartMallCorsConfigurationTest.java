package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmartMallCorsConfigurationTest {
    @Test
    void decisionStreamAllowsFetchAuthenticationAndResumeHeaders() {
        var configuration = SmartMallCorsConfiguration.forDecisionStream("https://smart.example");

        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Last-Event-ID");
    }

    @Test
    void exchangeOnlyAllowsConfiguredH5OriginWithoutCredentials() {
        var configuration = SmartMallCorsConfiguration.forOrigin("https://smart.example");

        assertThat(configuration.checkOrigin("https://smart.example")).isEqualTo("https://smart.example");
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
        assertThat(configuration.getAllowedMethods()).containsExactly("POST", "OPTIONS");
        assertThat(configuration.getAllowCredentials()).isFalse();
    }

    @Test
    void localDevelopmentAllowsOnlyLoopbackHttpOrigin() {
        var configuration = SmartMallCorsConfiguration.forOrigin("http://127.0.0.1:5173");

        assertThat(configuration.checkOrigin("http://127.0.0.1:5173")).isEqualTo("http://127.0.0.1:5173");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> SmartMallCorsConfiguration.forOrigin("http://smart.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
