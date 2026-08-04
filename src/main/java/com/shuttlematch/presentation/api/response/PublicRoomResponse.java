package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.Room;

import java.time.OffsetDateTime;

/**
 * ルーム一覧(公開)のレスポンス表現。
 * <p>
 * 一覧は認証なしで誰でも取得できるため、「何が開催されているか」だけを載せる。
 * roomId・shareCode・作成者・参加者名簿は<b>意図的に含めない</b>。これらを載せると
 * ルームIDを総当たりせずに列挙でき、削除や試合表の作り直しといった破壊的な API を
 * 誰でも叩けてしまう(この API 群にはまだ認証が無い)。
 * <p>
 * かわりに {@link PublicRoomId} を返し、既にそのルームを知っているクライアントだけが
 * 自分の roomId と突き合わせてリンクを張れるようにしている。
 */
public record PublicRoomResponse(
        String publicId,
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        String status,
        int participantCount) {

    public static PublicRoomResponse from(Room room) {
        return new PublicRoomResponse(
                PublicRoomId.of(room.id()),
                room.title(),
                room.heldAt(),
                room.location(),
                room.capacity(),
                room.courtCount(),
                room.status().name(),
                room.participants().size());
    }
}
