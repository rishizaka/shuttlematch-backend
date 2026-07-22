package com.shuttlematch.application.notification;

import com.shuttlematch.domain.model.notification.ExpoPushToken;

import java.util.Map;
import java.util.Objects;

/**
 * 1 端末に送る Push 通知。
 *
 * @param to    宛先の Expo Push Token
 * @param title 通知のタイトル
 * @param body  本文
 * @param data  通知タップ時にアプリへ渡す付随データ(遷移先の roomId など)
 */
public record PushMessage(ExpoPushToken to, String title, String body, Map<String, String> data) {

    public PushMessage {
        Objects.requireNonNull(to, "to は null にできません");
        Objects.requireNonNull(title, "title は null にできません");
        Objects.requireNonNull(body, "body は null にできません");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
