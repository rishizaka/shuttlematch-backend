package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションから参加者を削除する(参加キャンセル)ユースケース。
 */
@Service
public class RemoveParticipantUseCase {

    private final RoomRepository roomRepository;

    public RemoveParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public void execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        boolean removed = room.removeParticipant(participantId);
        if (!removed) {
            throw new ResourceNotFoundException(
                    "参加者が見つかりません: " + participantId.value());
        }
        roomRepository.save(room);
    }
}
