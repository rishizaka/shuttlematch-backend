package com.shuttlematch.application.notification;

import com.shuttlematch.domain.model.room.RoomId;

/**
 * Push 通知のきっかけになるルームの出来事。
 * ユースケースが publish し、コミット後にリスナーが通知を送る。
 */
public final class RoomNotificationEvents {

    private RoomNotificationEvents() {
    }

    /** セットが開始された。 */
    public record SetStarted(RoomId roomId, int setNumber) {
    }

    /** ルームが終了した。 */
    public record RoomClosed(RoomId roomId) {
    }
}
