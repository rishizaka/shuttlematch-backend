package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 早退した参加者を在席(ACTIVE)に戻す。次回の再編成から編成対象に戻る。
 */
@Service
public class ReactivateParticipantUseCase {

    private final RoomRepository roomRepository;

    public ReactivateParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (!room.reactivateParticipant(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return roomRepository.save(room);
    }
}
