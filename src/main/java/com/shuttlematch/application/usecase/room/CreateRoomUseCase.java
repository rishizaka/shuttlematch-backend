package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションを作成するユースケース。
 */
@Service
public class CreateRoomUseCase {

    private final RoomRepository roomRepository;

    public CreateRoomUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(CreateRoomCommand command) {
        Room room = Room.create(
                command.title(),
                command.heldAt(),
                command.location(),
                command.capacity(),
                command.courtCount(),
                command.createdBy());
        return roomRepository.save(room);
    }
}
