package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指定ステータスのセッション一覧を取得するユースケース。
 * 募集中(OPEN)のセッションをトップページで公開する用途に用いる。
 */
@Service
public class ListRoomsUseCase {

    private final RoomRepository roomRepository;

    public ListRoomsUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<Room> execute(RoomStatus status) {
        return roomRepository.findByStatus(status);
    }
}
