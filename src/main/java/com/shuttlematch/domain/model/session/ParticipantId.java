package com.shuttlematch.domain.model.session;

import java.util.Objects;
import java.util.UUID;

/**
 * セッション参加者の識別子(値オブジェクト)。
 * session_participants テーブルの id に対応する。
 */
public record ParticipantId(UUID value) {

    public ParticipantId {
        Objects.requireNonNull(value, "ParticipantId は null にできません");
    }

    public static ParticipantId of(UUID value) {
        return new ParticipantId(value);
    }

    public static ParticipantId newId() {
        return new ParticipantId(UUID.randomUUID());
    }
}
