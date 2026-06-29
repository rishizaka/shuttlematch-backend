package com.shuttlematch.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 関連の設定プロパティ({@code app.cors.*})。
 *
 * @param allowedOrigins 許可するフロントエンドのオリジン一覧
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
