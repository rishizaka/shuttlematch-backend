package com.shuttlematch.domain.model.circle;

import java.util.Objects;
import java.util.UUID;

/**
 * サークルの識別子(値オブジェクト)。
 */
public record CircleId(UUID value) {

    public CircleId {
        Objects.requireNonNull(value, "CircleId は null にできません");
    }

    public static CircleId of(UUID value) {
        return new CircleId(value);
    }
}
