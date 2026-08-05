package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomStatus;

import java.time.OffsetDateTime;

/**
 * ルーム一覧(公開)のレスポンス表現。
 * <p>
 * 一覧は認証なしで誰でも取得できるため、開催中のルームでは「何が開催されているか」だけを
 * 載せる。roomId・shareCode・作成者・参加者名簿は<b>意図的に含めない</b>。これらを載せると
 * ルームIDを総当たりせずに列挙でき、削除や試合表の作り直しといった破壊的な API を
 * 誰でも叩けてしまう(この API 群にはまだ認証が無い)。かわりに {@link PublicRoomId} を返し、
 * 既にそのルームを知っているクライアントだけが自分の roomId と突き合わせてリンクを張れる。
 * <p>
 * <b>終了したルームだけは {@code id} を返す。</b>過去の試合表は記録として誰でも見られる
 * ようにするため。終了したルームは削除も試合表の変更もできないので(RoomStatus の
 * {@code allowsMatchChanges} と DeleteRoomUseCase)、roomId が知られても壊されない。
 */
public record PublicRoomResponse(
        String publicId,
        String id,
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        String status,
        int participantCount) {

    public static PublicRoomResponse from(Room room) {
        boolean closed = room.status() == RoomStatus.CLOSED;
        return new PublicRoomResponse(
                PublicRoomId.of(room.id()),
                closed ? room.id().value().toString() : null,
                room.title(),
                room.heldAt(),
                room.location(),
                room.capacity(),
                room.courtCount(),
                room.status().name(),
                room.participants().size());
    }
}
