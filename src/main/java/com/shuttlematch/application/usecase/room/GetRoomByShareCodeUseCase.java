package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 共有コードでルームを取得するユースケース。短縮URL(/r/{code})の解決に使う。
 */
@Service
public class GetRoomByShareCodeUseCase {

    private final RoomRepository roomRepository;

    public GetRoomByShareCodeUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public Room execute(String shareCode) {
        return roomRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + shareCode));
    }
}
