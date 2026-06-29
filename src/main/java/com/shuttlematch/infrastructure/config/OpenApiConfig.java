package com.shuttlematch.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi (Swagger UI) のメタ情報設定。
 * UI は /swagger-ui.html、OpenAPI ドキュメントは /v3/api-docs で参照できる。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shuttleMatchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShuttleMatch API")
                        .description("バドミントンサークル管理アプリのバックエンド API")
                        .version("0.0.1-SNAPSHOT"));
    }
}
