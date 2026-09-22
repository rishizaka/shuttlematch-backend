package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Participant;
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

/**
 * RemoveParticipantUseCase の生成後(GENERATED)の挙動を確認する。
 * 生成前(PREPARING/OPEN)の挙動は既存の RoomUseCaseTest 側で確認している。
 */
class RemoveParticipantUseCaseTest {

    private FakeRoomRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private RemoveParticipantUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        ReplanFutureSetsUseCase replan = new ReplanFutureSetsUseCase(
                roomRepository, matchScheduleRepository, new MatchingDomainService(new Random(1L)));
        useCase = new RemoveParticipantUseCase(roomRepository, matchScheduleRepository, replan);
    }

    /** かんたん作成と同じ形: 番号(1..N)の参加者+生成済みの試合表を持つルーム。 */
    private Room generatedRoom(int count, int courtCount, int setCount) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(), null, null, courtCount,
                UserId.of(UUID.randomUUID()));
        for (int i = 1; i <= count; i++) {
            room.addGuest(String.valueOf(i));
        }
        MatchSchedule schedule = new MatchingDomainService(new Random(1L))
                .generate(room.id(), room.activeParticipantIds(), courtCount, setCount);
        room.markGenerated();
        roomRepository.save(room);
        matchScheduleRepository.save(schedule);
        return room;
    }

    @Test
    @DisplayName("生成後・未開始なら、末尾の参加者を削除できる(行が消え、試合表からも消える)")
    void removesLastParticipantBeforeAnySetStarts() {
        Room room = generatedRoom(6, 1, 5);
        ParticipantId last = room.participants().get(5).id();

        useCase.execute(room.id(), last);

        Room reloaded = roomRepository.findById(room.id()).orElseThrow();
        assertThat(reloaded.participants()).hasSize(5);
        assertThat(reloaded.participants()).noneMatch(p -> p.id().equals(last));

        MatchSchedule schedule = matchScheduleRepository.findByRoomId(room.id()).orElseThrow();
        boolean stillReferenced = schedule.matches().stream().anyMatch(
                m -> List.of(m.pairA().player1(), m.pairA().player2(),
                        m.pairB().player1(), m.pairB().player2()).contains(last));
        assertThat(stillReferenced)
                .as("削除した参加者が試合データにまだ残っていると、DBでは外部キー制約違反になる")
                .isFalse();
    }

    @Test
    @DisplayName("生成後、末尾以外の参加者を削除しようとすると例外(番号の繰り上がりを避ける)")
    void rejectsNonLastParticipant() {
        Room room = generatedRoom(6, 1, 5);
        ParticipantId notLast = room.participants().get(2).id();

        assertThatThrownBy(() -> useCase.execute(room.id(), notLast))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("一番後ろ");

        // 何も変わっていない。
        assertThat(roomRepository.findById(room.id()).orElseThrow().participants()).hasSize(6);
    }

    @Test
    @DisplayName("1セットでも開始した後は、末尾でも削除できない")
    void rejectsAfterAnySetStarted() {
        Room room = generatedRoom(6, 1, 5);
        ParticipantId last = room.participants().get(5).id();
        MatchSchedule schedule = matchScheduleRepository.findByRoomId(room.id()).orElseThrow();
        List<Match> started = schedule.matches().stream()
                .map(m -> m.setNumber() == 1 ? m.withStartedAt(OffsetDateTime.now()) : m)
                .toList();
        matchScheduleRepository.save(new MatchSchedule(room.id(), started));

        assertThatThrownBy(() -> useCase.execute(room.id(), last))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("開始した後");

        assertThat(roomRepository.findById(room.id()).orElseThrow().participants()).hasSize(6);
    }

    @Test
    @DisplayName("終了済みルームでは削除できない")
    void rejectsWhenClosed() {
        Room room = generatedRoom(6, 1, 5);
        room.close();
        roomRepository.save(room);
        ParticipantId last = room.participants().get(5).id();

        assertThatThrownBy(() -> useCase.execute(room.id(), last))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("存在しない参加者は ResourceNotFoundException")
    void throwsWhenParticipantMissing() {
        Room room = generatedRoom(6, 1, 5);

        assertThatThrownBy(() -> useCase.execute(room.id(), ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- インメモリ実装 ---

    private static final class FakeRoomRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();

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
            Room room = store.get(roomId);
            if (room == null) return Optional.empty();
            // 保存済みの Room をそのまま返さず reconstitute し直す。呼び出し側の
            // in-place な変更(mark系メソッドはparticipantsリストを直接書き換える)が、
            // 保存(save)前の状態に波及しないようにするため
            // (MarkParticipantLeftUseCaseTest の同様のフェイクと同じ考え方)。
            return Optional.of(Room.reconstitute(
                    room.id(), room.shareCode(), room.title(), room.heldAt(), room.location(),
                    room.capacity(), room.courtCount(), room.status(), room.createdBy(),
                    room.participants(), room.fixedPairs()));
        }

        @Override
        public Optional<Room> findByShareCode(String shareCode) {
            return store.values().stream()
                    .filter(r -> shareCode.equals(r.shareCode()))
                    .findFirst();
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(r -> r.status() == status).toList();
        }

        @Override
        public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
            return 0;
        }

        @Override
        public List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore) {
            return List.of();
        }

        @Override
        public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return List.copyOf(store.values());
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
    }
}
