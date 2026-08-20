package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloseExpiredRoomsUseCaseTest {

    private FakeRoomRepository roomRepository;
    private CloseExpiredRoomsUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        useCase = new CloseExpiredRoomsUseCase(roomRepository);
    }

    private Room newRoom() {
        return Room.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("期限切れルームをすべて終了し、件数を返す")
    void closesAllExpiredRooms() {
        Room a = newRoom();
        Room b = newRoom();
        roomRepository.save(a);
        roomRepository.save(b);
        roomRepository.expired = List.of(a, b);

        int closed = useCase.execute(OffsetDateTime.now());

        assertThat(closed).isEqualTo(2);
        assertThat(roomRepository.findById(a.id()).orElseThrow().status())
                .isEqualTo(RoomStatus.CLOSED);
        assertThat(roomRepository.findById(b.id()).orElseThrow().status())
                .isEqualTo(RoomStatus.CLOSED);
    }

    @Test
    @DisplayName("期限切れルームが無ければ何もせず 0 を返す")
    void doesNothingWhenNoExpiredRooms() {
        Room a = newRoom();
        roomRepository.save(a);

        int closed = useCase.execute(OffsetDateTime.now());

        assertThat(closed).isZero();
        assertThat(roomRepository.findById(a.id()).orElseThrow().status())
                .isNotEqualTo(RoomStatus.CLOSED);
    }

    @Test
    @DisplayName("リポジトリへ渡す閾値は now - TTL(36時間)になる")
    void queriesWithTtlThreshold() {
        OffsetDateTime now = OffsetDateTime.now();

        useCase.execute(now);

        assertThat(roomRepository.lastCreatedBefore)
                .isEqualTo(now.minus(CloseExpiredRoomsUseCase.ROOM_TTL));
    }

    private static final class FakeRoomRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();
        /** findNotClosedCreatedBefore が返す「期限切れ」ルーム。 */
        List<Room> expired = new ArrayList<>();
        OffsetDateTime lastCreatedBefore;

        @Override
        public Room save(Room room) {
            store.put(room.id(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId);
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId));
        }

        @Override
        public Optional<Room> findByShareCode(String shareCode) {
            return store.values().stream()
                    .filter(r -> shareCode.equals(r.shareCode()))
                    .findFirst();
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(s -> s.status() == status).toList();
        }

        @Override
        public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
            return 0;
        }

        @Override
        public List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore) {
            this.lastCreatedBefore = createdBefore;
            return expired;
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
