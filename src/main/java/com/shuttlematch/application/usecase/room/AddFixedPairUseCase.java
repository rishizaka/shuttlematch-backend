package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 固定ペア(常に同じチームで組む2人)を追加するユースケース。
 */
@Service
public class AddFixedPairUseCase {

    private final RoomRepository roomRepository;

    public AddFixedPairUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId a, ParticipantId b) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));
        room.addFixedPair(a, b);
        return roomRepository.save(room);
    }
}
