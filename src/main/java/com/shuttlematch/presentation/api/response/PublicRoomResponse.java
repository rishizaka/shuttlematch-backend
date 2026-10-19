package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.Room;

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
 * <b>終了したルームでも {@code id} は返さない。</b>以前は「過去の試合表は記録として誰でも
 * 見られるように」終了したものだけ id を載せていたが、2026-10-19 にやめた。練習会の
 * 参加者の並びは身内の記録であって、通りすがりに一覧から辿れる必要は無いため。
 * 開催中と同じく、作成・参加した端末(自分の roomId と突き合わせる)と共有URLを持つ人だけが
 * 開ける。<b>shareCode もここに載せないこと</b>(載せると一覧から順に削除できてしまう)。
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
        return from(room, false);
    }

    /**
     * {@code includeRoomId} が true のときだけ roomId を載せる。
     * 管理者トークンを提示した運営者本人の一覧でのみ true になる(AdminAccess 参照)。
     */
    public static PublicRoomResponse from(Room room, boolean includeRoomId) {
        return new PublicRoomResponse(
                PublicRoomId.of(room.id()),
                includeRoomId ? room.id().value().toString() : null,
                room.title(),
                room.heldAt(),
                room.location(),
                room.capacity(),
                room.courtCount(),
                room.status().name(),
                room.participants().size());
    }
}
