package com.shuttlematch.domain.model.notification;

import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;

import java.util.Objects;
import java.util.Optional;

/**
 * ルーム単位の Push 購読(値オブジェクト)。「このルームの通知をこの端末に送る」。
 *
 * <p>宛先をユーザーではなくルーム購読で持つのは、参加者のほとんどが user_id を持たない
 * ゲスト(番号だけの参加者)で、ユーザー単位では宛先を解決できないため。
 *
 * <p>{@code participantId} は端末が自己申告した「自分の番号」。任意で、あれば
 * 通知を個別化できる(「あなたの試合です」)。
 */
public record PushSubscription(
        RoomId roomId,
        ExpoPushToken token,
        Optional<ParticipantId> participantId,
        PushPlatform platform) {

    public PushSubscription {
        Objects.requireNonNull(roomId, "roomId は null にできません");
        Objects.requireNonNull(token, "token は null にできません");
        Objects.requireNonNull(participantId, "participantId は null にできません(空は Optional.empty())");
        Objects.requireNonNull(platform, "platform は null にできません");
    }
}
