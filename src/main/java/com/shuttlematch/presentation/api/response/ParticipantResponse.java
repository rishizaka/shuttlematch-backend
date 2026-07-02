package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.Participant;

/**
 * 参加者のレスポンス表現。
 */
public record ParticipantResponse(
        String id, String userId, String guestName, boolean guest, String status) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(
                participant.id().value().toString(),
                participant.userId() == null ? null : participant.userId().value().toString(),
                participant.guestName(),
                participant.isGuest(),
                participant.status().name());
    }
}
