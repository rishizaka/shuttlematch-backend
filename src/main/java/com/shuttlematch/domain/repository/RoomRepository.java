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

    /** 指定ステータスのセッションを開催日時の昇順で取得する。 */
    List<Room> findByStatus(RoomStatus status);

    /** 指定ユーザーが since 以降に作成したルーム数(作成回数制限のチェック用)。 */
    int countCreatedSince(UserId createdBy, OffsetDateTime since);
}
