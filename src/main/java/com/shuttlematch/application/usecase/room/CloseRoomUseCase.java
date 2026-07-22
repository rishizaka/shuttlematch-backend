package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.notification.RoomNotificationEvents;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションを終了する(履歴として残す)ユースケース。試合表はそのまま保持される。
 */
@Service
public class CloseRoomUseCase {

    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CloseRoomUseCase(
            RoomRepository roomRepository, ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Room execute(RoomId roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        room.close();
        Room closed = roomRepository.save(room);

        // 参加者に終了を知らせる。送信はコミット後(リスナー側)。
        eventPublisher.publishEvent(new RoomNotificationEvents.RoomClosed(roomId));
        return closed;
    }
}
