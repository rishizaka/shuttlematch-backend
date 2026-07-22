package com.shuttlematch.application.usecase.notification;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ルームの Push 通知を購読する(端末を通知先として登録する)ユースケース。
 * 同じ端末の再登録は上書きになるので、「自分の番号」を設定した後に呼び直せば個別化が効く。
 */
@Service
public class SubscribeRoomPushUseCase {

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoomRepository roomRepository;

    public SubscribeRoomPushUseCase(
            PushSubscriptionRepository subscriptionRepository,
            RoomRepository roomRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public void execute(
            RoomId roomId,
            ExpoPushToken token,
            Optional<ParticipantId> participantId,
            PushPlatform platform) {
        // 存在しないルームの購読を作らない(外部キー違反ではなく 404 で返すため)。
        if (roomRepository.findById(roomId).isEmpty()) {
            throw new ResourceNotFoundException("セッションが見つかりません: " + roomId.value());
        }
        subscriptionRepository.save(new PushSubscription(roomId, token, participantId, platform));
    }
}
