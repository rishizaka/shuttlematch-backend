package com.shuttlematch.application.usecase.notification;

import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ルームの Push 通知の購読を解除するユースケース。
 * 存在しない購読の解除は何もしない(冪等)。
 */
@Service
public class UnsubscribeRoomPushUseCase {

    private final PushSubscriptionRepository subscriptionRepository;

    public UnsubscribeRoomPushUseCase(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public void execute(RoomId roomId, ExpoPushToken token) {
        subscriptionRepository.delete(roomId, token);
    }
}
