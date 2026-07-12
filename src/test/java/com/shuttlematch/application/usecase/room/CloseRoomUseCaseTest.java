package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloseSessionUseCaseTest {

    private FakeSessionRepository roomRepository;
    private CloseRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        useCase = new CloseRoomUseCase(roomRepository);
    }

    @Test
    @DisplayName("セッションを終了済みにして返す")
    void closesSession() {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        roomRepository.save(room);

        Room result = useCase.execute(room.id());

        assertThat(result.status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(roomRepository.findById(room.id()).orElseThrow().status())
                .isEqualTo(RoomStatus.CLOSED);
    }

    @Test
    @DisplayName("存在しないセッションは ResourceNotFoundException")
    void throwsWhenSessionMissing() {
        assertThatThrownBy(() -> useCase.execute(RoomId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeSessionRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();

        @Override
        public Room save(Room room) {
            store.put(room.id(), room);
            return room;
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId));
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(s -> s.status() == status).toList();
        }

        @Override
        public int countCreatedSince(com.shuttlematch.domain.model.user.UserId createdBy, java.time.OffsetDateTime since) {
            return 0;
        }

        @Override
        public java.util.List<Room> findNotClosedCreatedBefore(java.time.OffsetDateTime createdBefore) {
            return java.util.List.of();
        }

        @Override
        public List<Room> search(RoomStatus status, java.time.OffsetDateTime heldFrom, java.time.OffsetDateTime heldTo) {
            return store.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .filter(r -> heldFrom == null || !r.heldAt().isBefore(heldFrom))
                    .filter(r -> heldTo == null || r.heldAt().isBefore(heldTo))
                    .toList();
        }
    }
}
