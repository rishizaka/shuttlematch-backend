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

class AddSetsUseCaseTest {

    private FakeSessionRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private MatchingDomainService matchingDomainService;
    private AddSetsUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        matchingDomainService = new MatchingDomainService(new Random(100L));
        useCase = new AddSetsUseCase(
                roomRepository, matchScheduleRepository, matchingDomainService);
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
    @DisplayName("既存スケジュールにセットを追加し、保存して返す")
    void addsSetsToExistingSchedule() {
        Room room = openSessionWithGuests(6);
        // 既存: 1コート×3セット = 3試合
        MatchSchedule base = matchingDomainService.generate(room.id(), room.participantIds(), 1, 3);
        matchScheduleRepository.save(base);

        MatchSchedule result = useCase.execute(room.id(), 2);

        assertThat(result.setCount()).isEqualTo(5);
        assertThat(result.size()).isEqualTo(5);
        assertThat(matchScheduleRepository.findByRoomId(room.id())).contains(result);
        // 追加はあくまで置き換え保存(削除→保存)なので1件だけ残る
        assertThat(matchScheduleRepository.count(room.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("スケジュール未生成なら ResourceNotFoundException")
    void throwsWhenScheduleNotGenerated() {
        Room room = openSessionWithGuests(6);

        assertThatThrownBy(() -> useCase.execute(room.id(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(RoomId.newId(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
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
            store.removeIf(s -> s.roomId().equals(roomId));
        }

        @Override
        public Optional<MatchSchedule> startSet(
                RoomId roomId, int setNumber, OffsetDateTime startedAt) {
            return findByRoomId(roomId);
        }

        long count(RoomId roomId) {
            return store.stream().filter(s -> s.roomId().equals(roomId)).count();
        }
    }
}
