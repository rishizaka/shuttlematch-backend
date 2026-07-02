package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ゲスト参加者の名前(ニックネーム)を変更するユースケース。番号のまま作った参加者に後から名前を付ける。
 */
@Service
public class RenameParticipantUseCase {

    private final RoomRepository roomRepository;

    public RenameParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId participantId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名前を入力してください");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (!room.renameParticipant(participantId, name.trim())) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return roomRepository.save(room);
    }
}
