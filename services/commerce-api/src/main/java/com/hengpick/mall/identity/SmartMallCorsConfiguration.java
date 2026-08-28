package com.hengpick.mall.identity;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class SmartMallCorsConfiguration {
    @Bean
    CorsFilter smartMallCorsFilter(
            @Value("${hengpick.identity.smart-mall-h5-origin:http://127.0.0.1:5173}") String h5Origin) {
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/smart-mall/sessions/exchange", forOrigin(h5Origin));
        return new CorsFilter(source);
    }

    static CorsConfiguration forOrigin(String h5Origin) {
        var uri = URI.create(h5Origin);
        boolean isHttps = "https".equals(uri.getScheme());
        boolean isLoopbackHttp = "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!isHttps && !isLoopbackHttp) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Smart Mall H5 Origin 必须是 HTTPS，或本地回环 HTTP Origin");
        }
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(h5Origin));
        configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(600L);
        return configuration;
    }
}
