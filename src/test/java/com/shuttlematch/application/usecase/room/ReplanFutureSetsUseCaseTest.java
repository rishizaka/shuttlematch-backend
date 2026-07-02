package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.ParticipantId;
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

class ReplanFutureSetsUseCaseTest {

    private FakeSessionRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private MatchingDomainService matchingDomainService;
    private ReplanFutureSetsUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        matchingDomainService = new MatchingDomainService(new Random(200L));
        useCase = new ReplanFutureSetsUseCase(
                roomRepository, matchScheduleRepository, matchingDomainService);
    }

    private Room openSessionWithGuests(int count) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            room.addGuest("ゲスト" + i);
        }
        room.markGenerated();
        roomRepository.save(room);
        return room;
    }

    /** 第1..upTo セットを開始済みにしたスケジュールを保存する。 */
    private void saveScheduleWithStarted(Room room, int totalSets, int upTo) {
        MatchSchedule base =
                matchingDomainService.generate(room.id(), room.activeParticipantIds(), 1, totalSets);
        OffsetDateTime t = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        List<Match> started = base.matches().stream()
                .map(m -> m.setNumber() <= upTo ? m.withStartedAt(t.plusMinutes(m.setNumber())) : m)
                .toList();
        matchScheduleRepository.save(new MatchSchedule(room.id(), started));
    }

    @Test
    @DisplayName("早退者を除外して未開始セットを再編成し、合計セット数は維持する")
    void replansFutureExcludingLeaver() {
        Room room = openSessionWithGuests(6);
        saveScheduleWithStarted(room, 5, 2);

        // 参加者の1人を早退にする
        ParticipantId leaver = room.participants().get(0).id();
        room.markParticipantLeft(leaver);
        roomRepository.save(room);

        MatchSchedule result = useCase.execute(room.id());

        assertThat(result.setCount()).isEqualTo(5);
        boolean leaverInFuture = result.matches().stream()
                .filter(m -> m.setNumber() > 2)
                .anyMatch(m -> containsParticipant(m, leaver));
        assertThat(leaverInFuture).isFalse();
        // 保存は置き換え(削除→保存)で1件
        assertThat(matchScheduleRepository.count(room.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("スケジュール未生成なら ResourceNotFoundException")
    void throwsWhenScheduleNotGenerated() {
        Room room = openSessionWithGuests(6);
        assertThatThrownBy(() -> useCase.execute(room.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(RoomId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private boolean containsParticipant(Match m, ParticipantId p) {
        return m.pairA().player1().equals(p) || m.pairA().player2().equals(p)
                || m.pairB().player1().equals(p) || m.pairB().player2().equals(p);
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
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(s -> s.status() == status).toList();
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
