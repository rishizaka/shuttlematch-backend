package com.shuttlematch.domain.repository;

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
