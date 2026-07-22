package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.RoomId;

import java.util.List;

/**
 * ルーム単位の Push 購読の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface PushSubscriptionRepository {

    /**
     * 購読を登録する。同じ(ルーム, 端末)の組が既にあれば更新する(upsert)。
     * 端末は「自分の番号」を後から設定することがあるため、再登録で participantId を更新できる。
     */
    void save(PushSubscription subscription);

    /** ルームの購読を全件返す(通知の送信先)。 */
    List<PushSubscription> findByRoomId(RoomId roomId);

    /** 指定ルームの、その端末の購読を解除する。 */
    void delete(RoomId roomId, ExpoPushToken token);

    /**
     * その端末の購読を全ルームから削除する。
     * Expo から DeviceNotRegistered が返った(アンインストール等)ときに使う。
     */
    void deleteByToken(ExpoPushToken token);
}
