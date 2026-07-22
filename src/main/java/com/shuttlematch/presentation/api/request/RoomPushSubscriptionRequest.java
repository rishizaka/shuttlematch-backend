package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * ルームの Push 購読の登録リクエスト。
 *
 * @param expoToken     Expo Push Token(ExponentPushToken[...])。FCM/APNs のトークンではない
 * @param participantId 端末が自己申告した「自分の番号」の参加者 ID。任意(通知の個別化に使う)
 * @param platform      ios / android
 */
public record RoomPushSubscriptionRequest(
        @NotBlank String expoToken,
        UUID participantId,
        @NotBlank @Pattern(regexp = "(?i)ios|android", message = "platform は ios か android です")
        String platform) {
}
