package com.shuttlematch.domain.model.room;

import java.util.Objects;
import java.util.UUID;

/**
 * セッションの識別子(値オブジェクト)。
 */
public record RoomId(UUID value) {

    public RoomId {
        Objects.requireNonNull(value, "RoomId は null にできません");
    }

    public static RoomId of(UUID value) {
        return new RoomId(value);
    }

    public static RoomId newId() {
        return new RoomId(UUID.randomUUID());
    }
}
