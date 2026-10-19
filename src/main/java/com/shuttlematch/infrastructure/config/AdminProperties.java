package com.shuttlematch.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理者アクセスの設定プロパティ({@code app.admin.*})。
 *
 * <p>運営者トークンが「このルームを作った端末か」を見るのに対し、こちらは
 * <b>運営者本人が全ルームを閲覧する</b>ための秘密。ルームごとではなくサイト全体に1本。
 *
 * @param token 管理者トークンの平文。未設定(空)なら管理者アクセスそのものを無効にする
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String token) {

    public AdminProperties {
        token = token == null ? "" : token.trim();
    }
}
