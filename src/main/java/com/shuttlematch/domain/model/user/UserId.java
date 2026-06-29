package com.shuttlematch.domain.model.user;

import java.util.Objects;
import java.util.UUID;

/**
 * ユーザーの識別子(値オブジェクト)。
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId は null にできません");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
