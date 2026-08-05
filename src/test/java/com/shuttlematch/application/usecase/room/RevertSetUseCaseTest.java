package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RevertSetUseCaseTest {

    private FakeRepo repo;
    private FakeRoomRepo roomRepo;
    private RevertSetUseCase useCase;
    private final RoomId roomId = RoomId.newId();

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        roomRepo = new FakeRoomRepo();
        roomRepo.save(openRoom());
        useCase = new RevertSetUseCase(roomRepo, repo);
    }

    /** roomId をそのまま持つ、開催中(GENERATED 相当)のルーム。 */
    private com.shuttlematch.domain.model.room.Room openRoom() {
        return com.shuttlematch.domain.model.room.Room.reconstitute(
                roomId, "code1234", "練習会", OffsetDateTime.parse("2026-06-30T09:00:00Z"),
                null, null, 1, com.shuttlematch.domain.model.room.RoomStatus.GENERATED,
                com.shuttlematch.domain.model.user.UserId.of(java.util.UUID.randomUUID()),
                List.of());
    }

    private Match match(int setNumber, OffsetDateTime startedAt) {
        Pair a = new Pair(ParticipantId.newId(), ParticipantId.newId());
        Pair b = new Pair(ParticipantId.newId(), ParticipantId.newId());
        return new Match(MatchNumber.of(setNumber), setNumber, 1, a, b, startedAt);
    }

    @Test
    @DisplayName("進行中(最新開始)セットを開始前に戻せる")
    void revertsActiveSet() {
        OffsetDateTime t = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        repo.schedule = new MatchSchedule(roomId,
                List.of(match(1, t), match(2, t.plusMinutes(10)), match(3, null)));

        MatchSchedule result = useCase.execute(roomId, 2);

        var set2 = result.matches().stream().filter(m -> m.setNumber() == 2).findFirst().orElseThrow();
        var set1 = result.matches().stream().filter(m -> m.setNumber() == 1).findFirst().orElseThrow();
        assertThat(set2.isStarted()).isFalse();
        assertThat(set1.isStarted()).isTrue();
    }

    @Test
    @DisplayName("進行中でないセットは戻せない(IllegalState)")
    void cannotRevertNonActive() {
        OffsetDateTime t = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        repo.schedule = new MatchSchedule(roomId,
                List.of(match(1, t), match(2, t.plusMinutes(10)), match(3, null)));

        assertThatThrownBy(() -> useCase.execute(roomId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("開始済みが無ければ IllegalState")
    void cannotRevertWhenNothingStarted() {
        repo.schedule = new MatchSchedule(roomId, List.of(match(1, null), match(2, null)));
        assertThatThrownBy(() -> useCase.execute(roomId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("スケジュールが無ければ ResourceNotFoundException")
    void throwsWhenNoSchedule() {
        assertThatThrownBy(() -> useCase.execute(roomId, 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }


    /** ルームの状態(終了済みか)だけを見るための最小のフェイク。 */
    private static final class FakeRoomRepo
            implements com.shuttlematch.domain.repository.RoomRepository {
        private final java.util.Map<RoomId, com.shuttlematch.domain.model.room.Room> store =
                new java.util.HashMap<>();

        @Override
        public com.shuttlematch.domain.model.room.Room save(
                com.shuttlematch.domain.model.room.Room room) {
            store.put(room.id(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId);
        }

        @Override
        public Optional<com.shuttlematch.domain.model.room.Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId));
        }

        @Override
        public Optional<com.shuttlematch.domain.model.room.Room> findByShareCode(String shareCode) {
            return Optional.empty();
        }

        @Override
        public List<com.shuttlematch.domain.model.room.Room> findByStatus(
                com.shuttlematch.domain.model.room.RoomStatus status) {
            return store.values().stream().filter(r -> r.status() == status).toList();
        }

        @Override
        public int countCreatedSince(
                com.shuttlematch.domain.model.user.UserId createdBy,
                OffsetDateTime since) {
            return 0;
        }

        @Override
        public List<com.shuttlematch.domain.model.room.Room> search(
                com.shuttlematch.domain.model.room.RoomStatus status,
                OffsetDateTime heldFrom,
                OffsetDateTime heldTo) {
            return List.copyOf(store.values());
        }

        @Override
        public List<com.shuttlematch.domain.model.room.Room> findNotClosedCreatedBefore(
                OffsetDateTime before) {
            return List.of();
        }
    }

    private static final class FakeRepo implements MatchScheduleRepository {
        private MatchSchedule schedule;

        @Override
        public MatchSchedule save(MatchSchedule s) {
            this.schedule = s;
            return s;
        }

        @Override
        public Optional<MatchSchedule> findByRoomId(RoomId id) {
            return Optional.ofNullable(schedule);
        }

        @Override
        public void deleteByRoomId(RoomId id) {
            schedule = null;
        }

        @Override
        public Optional<MatchSchedule> startSet(RoomId id, int setNumber, OffsetDateTime startedAt) {
            if (schedule == null) {
                return Optional.empty();
            }
            List<Match> updated = new ArrayList<>();
            for (Match m : schedule.matches()) {
                updated.add(m.setNumber() == setNumber ? m.withStartedAt(startedAt) : m);
            }
            schedule = new MatchSchedule(id, updated);
            return Optional.of(schedule);
        }
    }
}
