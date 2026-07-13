package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.Room;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ルームのレスポンス表現。
 */
public record RoomResponse(
        String id,
        String shareCode,
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        String status,
        String createdBy,
        int participantCount,
        List<ParticipantResponse> participants) {

    public static RoomResponse from(Room room) {
        List<ParticipantResponse> participants = room.participants().stream()
                .map(ParticipantResponse::from)
                .toList();
        return new RoomResponse(
                room.id().value().toString(),
                room.shareCode(),
                room.title(),
                room.heldAt(),
                room.location(),
                room.capacity(),
                room.courtCount(),
                room.status().name(),
                room.createdBy().value().toString(),
                participants.size(),
                participants);
    }
}
