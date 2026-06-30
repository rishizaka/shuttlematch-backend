package com.shuttlematch.domain.model.circle;

import java.util.Objects;
import java.util.UUID;

/**
 * 参加申請の識別子(値オブジェクト)。
 */
public record JoinRequestId(UUID value) {

    public JoinRequestId {
        Objects.requireNonNull(value, "JoinRequestId は null にできません");
    }

    public static JoinRequestId of(UUID value) {
        return new JoinRequestId(value);
    }

    public static JoinRequestId newId() {
        return new JoinRequestId(UUID.randomUUID());
    }
}
