package com.shuttlematch.application.notification;

import java.util.List;

/**
 * Push 通知の送信口(アプリケーション層のポート)。実装は infrastructure 側。
 *
 * <p>送信の失敗は業務処理を壊してはいけないため、実装は例外を投げずログに留める。
 */
public interface PushNotificationSender {

    /** まとめて送信する。無効になった端末の購読は実装側で掃除する。 */
    void send(List<PushMessage> messages);
}
