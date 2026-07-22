package com.shuttlematch.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ルームの出来事を受けて Push 通知を送るリスナー。
 *
 * <p>コミット後(AFTER_COMMIT)に動かす。通知の送信は外部 HTTP なので、
 * トランザクション内で行うと遅い上に、失敗が業務処理をロールバックさせてしまう。
 * 逆にコミット後なので「DB は更新されたが通知は届かない」ことは起こりうるが、
 * 通知が届かなくても試合は進むため許容する。
 */
@Component
public class RoomNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoomNotificationEventListener.class);

    private final RoomNotificationService notificationService;

    public RoomNotificationEventListener(RoomNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSetStarted(RoomNotificationEvents.SetStarted event) {
        try {
            notificationService.notifySetStarted(event.roomId(), event.setNumber());
        } catch (RuntimeException ex) {
            log.warn("セット開始の通知に失敗しました: room={}", event.roomId().value(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomClosed(RoomNotificationEvents.RoomClosed event) {
        try {
            notificationService.notifyRoomClosed(event.roomId());
        } catch (RuntimeException ex) {
            log.warn("ルーム終了の通知に失敗しました: room={}", event.roomId().value(), ex);
        }
    }
}
