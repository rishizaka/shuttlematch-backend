package com.shuttlematch.infrastructure.notification;

import com.shuttlematch.application.notification.PushMessage;
import com.shuttlematch.application.notification.PushNotificationSender;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Expo Push API 経由で通知を送る {@link PushNotificationSender} の実装。
 *
 * <p>FCM/APNs を直接叩かないので、backend はそれらの資格情報を持たない(EAS 側が保持する)。
 * 送信の失敗は業務処理を壊さないよう、例外を投げずにログへ残す。
 */
@Component
public class ExpoPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushNotificationSender.class);

    /** Expo が推奨する 1 リクエストあたりの上限。 */
    private static final int BATCH_SIZE = 100;

    private final RestClient restClient;
    private final PushSubscriptionRepository subscriptionRepository;
    private final boolean enabled;

    public ExpoPushNotificationSender(
            RestClient.Builder restClientBuilder,
            PushSubscriptionRepository subscriptionRepository,
            @Value("${app.push.expo-url:https://exp.host/--/api/v2/push/send}") String expoUrl,
            @Value("${app.push.enabled:true}") boolean enabled) {
        this.restClient = restClientBuilder.baseUrl(expoUrl).build();
        this.subscriptionRepository = subscriptionRepository;
        this.enabled = enabled;
    }

    @Override
    public void send(List<PushMessage> messages) {
        if (!enabled || messages.isEmpty()) {
            return;
        }
        for (int from = 0; from < messages.size(); from += BATCH_SIZE) {
            List<PushMessage> batch = messages.subList(
                    from, Math.min(from + BATCH_SIZE, messages.size()));
            try {
                sendBatch(batch);
            } catch (RuntimeException ex) {
                // 通知が届かなくても試合は進む。落とさずに記録だけ残す。
                log.warn("Push 通知の送信に失敗しました({}件)", batch.size(), ex);
            }
        }
    }

    private void sendBatch(List<PushMessage> batch) {
        List<Map<String, Object>> payload = new ArrayList<>(batch.size());
        for (PushMessage message : batch) {
            payload.add(Map.of(
                    "to", message.to().value(),
                    "title", message.title(),
                    "body", message.body(),
                    "data", message.data(),
                    "sound", "default",
                    // Android は通知チャンネルが無いと表示されない(アプリ側で作成済み)
                    "channelId", "match"));
        }

        ExpoPushResponse response = restClient.post()
                .body(payload)
                .retrieve()
                .body(ExpoPushResponse.class);

        if (response == null || response.data() == null) {
            log.warn("Expo Push API から想定外の応答: {}", response);
            return;
        }
        // 応答は送信順に返る。エラーの中でも DeviceNotRegistered だけは購読を消す。
        for (int i = 0; i < response.data().size() && i < batch.size(); i++) {
            ExpoPushTicket ticket = response.data().get(i);
            if (!"error".equals(ticket.status())) {
                continue;
            }
            String error = ticket.details() == null ? null : ticket.details().get("error");
            ExpoPushToken token = batch.get(i).to();
            if ("DeviceNotRegistered".equals(error)) {
                log.info("端末が無効になったため購読を削除します: {}", token.value());
                subscriptionRepository.deleteByToken(token);
            } else {
                log.warn("Push 通知が拒否されました: token={}, message={}", token.value(), ticket.message());
            }
        }
    }

    /** Expo Push API の応答。 */
    record ExpoPushResponse(List<ExpoPushTicket> data) {
    }

    /** 宛先ごとの結果。status は "ok" か "error"。 */
    record ExpoPushTicket(String status, String id, String message, Map<String, String> details) {
    }
}
