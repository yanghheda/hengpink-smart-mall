package com.hengpick.mall.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hengpick.commerce")
public record CommerceServiceProperties(
        @NotBlank String environment, @NotNull URI agentUrl, @NotBlank String datasetVersion) {}
