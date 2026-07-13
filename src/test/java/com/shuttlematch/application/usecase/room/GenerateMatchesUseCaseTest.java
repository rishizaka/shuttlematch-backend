package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import com.shuttlematch.domain.service.MatchingDomainService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerateMatchesUseCaseTest {

    private FakeSessionRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private GenerateMatchesUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        useCase = new GenerateMatchesUseCase(
                roomRepository,
                matchScheduleRepository,
                new MatchingDomainService(new Random(100L)));
    }

    private Room openSessionWithGuests(int count) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            room.addGuest("ゲスト" + i);
        }
        roomRepository.save(room);
        return room;
    }

    @Test
    @DisplayName("セッションの参加者から生成し、保存して返す。セッションは生成済みになる")
    void generatesAndPersistsSchedule() {
        Room room = openSessionWithGuests(8);

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(room.id()));

        // 1コート(デフォルト) × デフォルト10セット = 10試合
        assertThat(result.size()).isEqualTo(10);
        assertThat(matchScheduleRepository.findByRoomId(room.id())).contains(result);
        assertThat(roomRepository.findById(room.id()).orElseThrow().status())
                .isEqualTo(RoomStatus.GENERATED);
    }

    @Test
    @DisplayName("試合数を指定して生成できる")
    void generatesWithCustomMatchCount() {
        Room room = openSessionWithGuests(6);

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(room.id(), 5));

        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("再生成時は既存スケジュールを削除してから保存する")
    void regenerationDeletesExistingSchedule() {
        Room room = openSessionWithGuests(8);

        useCase.execute(new GenerateMatchesCommand(room.id()));
        useCase.execute(new GenerateMatchesCommand(room.id()));

        assertThat(matchScheduleRepository.deleteCount).isEqualTo(2);
        assertThat(matchScheduleRepository.count(room.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("参加者が4人未満なら例外を投げ、保存しない")
    void doesNotPersistWhenTooFewParticipants() {
        Room room = openSessionWithGuests(3);

        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(room.id())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(matchScheduleRepository.findByRoomId(room.id())).isEmpty();
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(RoomId.newId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("終了済みセッションでは生成できない(IllegalStateException)")
    void throwsWhenSessionClosed() {
        Room closed = Room.reconstitute(
                RoomId.newId(), "testcode", "終了", OffsetDateTime.now(),
                null, null, null, RoomStatus.CLOSED,                 UserId.of(UUID.randomUUID()), List.of());
        roomRepository.save(closed);

        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(closed.id())))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- インメモリ実装 ---

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

    private static final class FakeMatchScheduleRepository implements MatchScheduleRepository {
        private final List<MatchSchedule> store = new ArrayList<>();
        int deleteCount = 0;

        @Override
        public MatchSchedule save(MatchSchedule schedule) {
            store.add(schedule);
            return schedule;
        }

        @Override
        public Optional<MatchSchedule> findByRoomId(RoomId roomId) {
            return store.stream()
                    .filter(s -> s.roomId().equals(roomId))
                    .reduce((first, second) -> second);
        }

        @Override
        public void deleteByRoomId(RoomId roomId) {
            deleteCount++;
            store.removeIf(s -> s.roomId().equals(roomId));
        }

        @Override
        public java.util.Optional<com.shuttlematch.domain.model.match.MatchSchedule> startSet(
                RoomId roomId, int setNumber, java.time.OffsetDateTime startedAt) {
            return findByRoomId(roomId);
        }

        long count(RoomId roomId) {
            return store.stream().filter(s -> s.roomId().equals(roomId)).count();
        }
    }
}
