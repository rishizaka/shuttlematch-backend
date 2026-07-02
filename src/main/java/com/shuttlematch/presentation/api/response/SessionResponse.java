package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.session.Session;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ルームのレスポンス表現。
 */
public record SessionResponse(
        String id,
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        String status,
        String createdBy,
        int participantCount,
        List<ParticipantResponse> participants) {

    public static SessionResponse from(Session session) {
        List<ParticipantResponse> participants = session.participants().stream()
                .map(ParticipantResponse::from)
                .toList();
        return new SessionResponse(
                session.id().value().toString(),
                session.title(),
                session.heldAt(),
                session.location(),
                session.capacity(),
                session.courtCount(),
                session.status().name(),
                session.createdBy().value().toString(),
                participants.size(),
                participants);
    }
}
