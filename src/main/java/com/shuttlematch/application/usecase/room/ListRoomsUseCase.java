package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 条件に合うセッション一覧を取得するユースケース。
 * トップページ(本日の開催)や過去の開催一覧で用いる。
 * 各条件は null なら適用しない。
 */
@Service
public class ListRoomsUseCase {

    private final RoomRepository roomRepository;

    public ListRoomsUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<Room> execute(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
        return roomRepository.search(status, heldFrom, heldTo);
    }
}
