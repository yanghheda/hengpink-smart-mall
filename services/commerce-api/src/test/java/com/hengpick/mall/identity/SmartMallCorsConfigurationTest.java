package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmartMallCorsConfigurationTest {
    @Test
    void h5ApiAllowsAuthenticatedReadWriteAndResumeHeaders() {
        var configuration = SmartMallCorsConfiguration.forH5Api("https://smart.example");

        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).contains(
                "Authorization", "Content-Type", "Last-Event-ID", "Idempotency-Key");
    }

    @Test
    void exchangeOnlyAllowsConfiguredH5OriginWithoutCredentials() {
        var configuration = SmartMallCorsConfiguration.forH5Api("https://smart.example");

        assertThat(configuration.checkOrigin("https://smart.example")).isEqualTo("https://smart.example");
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
        assertThat(configuration.getAllowCredentials()).isFalse();
    }

    @Test
    void localDevelopmentAllowsOnlyLoopbackHttpOrigin() {
        var configuration = SmartMallCorsConfiguration.forH5Api("http://127.0.0.1:5173");

        assertThat(configuration.checkOrigin("http://127.0.0.1:5173")).isEqualTo("http://127.0.0.1:5173");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> SmartMallCorsConfiguration.forH5Api("http://smart.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
