package com.shuttlematch.presentation.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shuttlematch.domain.model.room.Room;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ルームのレスポンス表現。
 * <p>
 * {@code organizerToken} はルーム作成時のレスポンスにだけ含まれる({@link #fromCreated})。
 * 参照系で返すと共有リンクを開いた全員が運営者になれてしまうため、{@link #from} は決して含めない。
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
        List<ParticipantResponse> participants,
        List<FixedPairResponse> fixedPairs,
        /** 簡易作成(人数を指定してその場で番号参加者+試合表を作る)ルームか。 */
        boolean quickCreated,
        /**
         * 作成時だけ入る運営者トークンの平文。参照系のレスポンスには項目ごと出さない
         * (他の null 項目の出方は変えないよう、この項目だけに NON_NULL を付ける)。
         */
        @JsonInclude(JsonInclude.Include.NON_NULL) String organizerToken) {

    /** 作成時のレスポンス。発行された運営者トークンの平文を1度だけ含める。 */
    public static RoomResponse fromCreated(Room room) {
        RoomResponse base = from(room);
        return new RoomResponse(
                base.id(), base.shareCode(), base.title(), base.heldAt(), base.location(),
                base.capacity(), base.courtCount(), base.status(), base.createdBy(),
                base.participantCount(), base.participants(), base.fixedPairs(),
                base.quickCreated(), room.issuedOrganizerToken());
    }

    public static RoomResponse from(Room room) {
        List<ParticipantResponse> participants = room.participants().stream()
                .map(ParticipantResponse::from)
                .toList();
        List<FixedPairResponse> fixedPairs = room.fixedPairs().stream()
                .map(FixedPairResponse::from)
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
                participants,
                fixedPairs,
                room.quickCreated(),
                null);
    }
}
