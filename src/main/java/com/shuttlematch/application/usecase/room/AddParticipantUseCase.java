package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションに参加者(登録ユーザーまたはゲスト)を追加するユースケース。
 */
@Service
public class AddParticipantUseCase {

    private final RoomRepository roomRepository;

    public AddParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(AddParticipantCommand command) {
        Room room = roomRepository.findById(command.roomId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + command.roomId().value()));

        if (command.isGuest()) {
            room.addGuest(command.guestName());
        } else {
            room.addUser(command.userId());
        }
        return roomRepository.save(room);
    }
}
