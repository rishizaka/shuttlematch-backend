package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者を早退にする(在席状態を LEFT に更新)。履歴は残り、未開始セットの編成対象から外れる。
 */
@Service
public class MarkParticipantLeftUseCase {

    private final RoomRepository roomRepository;

    public MarkParticipantLeftUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (!room.markParticipantLeft(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return roomRepository.save(room);
    }
}
