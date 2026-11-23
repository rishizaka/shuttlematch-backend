package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * セッション集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface RoomRepository {

    Room save(Room room);

    /**
     * 「空き」枠({@link Participant#isClaimableSlot()}: 番号のまま・フリー・
     * 遅刻者・ビジターのいずれか)のうち、番号が一番若いものを1つだけ選んで名前を付け、
     * その participantId を返す(無ければ空)。同時に複数人が呼んでも、それぞれ別の
     * 枠が割り当てられる(取り合いにならない)。1セット目が始まる前の簡易作成ルームで
     * 「参加する」を押したときに使う。定員ぶん全員が名乗った後でも、早退などでフリーに
     * なった枠があればそこに詰められる。
     * <p>
     * デフォルト実装は集約経由(単一スレッド前提のテスト用フェイク向け)。JPA 実装は
     * 行ロック({@code FOR UPDATE SKIP LOCKED})で原子的に行う。
     */
    default Optional<ParticipantId> claimNextFreeSlot(RoomId roomId, String name) {
        return findById(roomId).flatMap(room -> {
            Optional<Participant> next = room.participants().stream()
                    .filter(p -> p.isActive() && p.isClaimableSlot())
                    .findFirst();
            next.ifPresent(p -> room.renameParticipant(p.id(), name));
            next.ifPresent(p -> save(room));
            return next.map(Participant::id);
        });
    }

    /**
     * ルームを削除する。配下の参加者・固定ペア・試合表は DB のカスケードで一緒に削除される。
     * 存在しない ID の場合は何もしない。
     */
    void deleteById(RoomId roomId);

    Optional<Room> findById(RoomId roomId);

    /** 共有コードでルームを取得する(短縮URL /r/{code} の解決用)。 */
    Optional<Room> findByShareCode(String shareCode);

    /** 指定ステータスのセッションを開催日時の昇順で取得する。 */
    List<Room> findByStatus(RoomStatus status);

    /**
     * 条件を組み合わせてセッションを検索する(開催日時の昇順)。
     * 各条件は null なら適用しない。heldFrom は以上、heldTo は未満。
     */
    List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo);

    /** 指定ユーザーが since 以降に作成したルーム数(作成回数制限のチェック用)。 */
    int countCreatedSince(UserId createdBy, OffsetDateTime since);

    /** 終了していないルームのうち、createdBefore より前に作成されたものを取得する(期限切れ自動終了用)。 */
    List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore);
}
