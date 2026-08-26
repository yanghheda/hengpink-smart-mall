package com.hengpick.mall.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Commerce API 的运行时接口文档配置。 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI commerceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("HengPick Commerce API")
                .version("v1")
                .description("运行时 Swagger 文档。对外接口契约以 packages/api-contracts/openapi.yaml 为准。"));
    }
}
