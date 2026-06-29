package com.shuttlematch.domain.model.session;

import java.util.Objects;
import java.util.UUID;

/**
 * セッションの識別子(値オブジェクト)。
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "SessionId は null にできません");
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }
}
