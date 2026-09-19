package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.ParticipantStatus;
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
 * executeMany() は「1回の読み込み・1回の保存」であることが要点(クラス javadoc の
 * lost update 対策)。ここでは save() の呼び出し回数と、失敗時に何も保存されないことを固定する。
 * また、試合表が生成済みなら早退と同時に未開始セットが自動で再編成されることも固定する
 * (2026-09-15・2026-09-19 に「早退にしたのに、セット追加すると未開始セットに残ったまま
 * だった」不具合が本番で起きたため)。
 */
class MarkParticipantLeftUseCaseTest {

    private FakeRoomRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private MarkParticipantLeftUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        ReplanFutureSetsUseCase replan = new ReplanFutureSetsUseCase(
                roomRepository, matchScheduleRepository, new MatchingDomainService(new Random(1L)));
        useCase = new MarkParticipantLeftUseCase(roomRepository, matchScheduleRepository, replan);
    }

    private Room roomWithGuests(int count) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            room.addGuest(String.valueOf(i + 1));
        }
        roomRepository.save(room);
        return room;
    }

    private List<ParticipantId> participantIdsInMatches(MatchSchedule schedule) {
        return schedule.matches().stream()
                .flatMap(m -> java.util.stream.Stream.of(
                        m.pairA().player1(), m.pairA().player2(),
                        m.pairB().player1(), m.pairB().player2()))
                .distinct()
                .toList();
    }

    @Test
    @DisplayName("executeMany: 指定した全員を早退にし、保存は1回だけ")
    void marksAllListedAsLeftInOneSave() {
        Room room = roomWithGuests(4);
        List<ParticipantId> targets =
                List.of(room.participants().get(0).id(), room.participants().get(2).id());

        roomRepository.saveCount = 0;
        Room result = useCase.executeMany(room.id(), targets);

        assertThat(result.participants().get(0).status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(result.participants().get(2).status()).isEqualTo(ParticipantStatus.LEFT);
        // 対象外は在席のまま。
        assertThat(result.participants().get(1).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(result.participants().get(3).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(roomRepository.saveCount).isEqualTo(1);
    }

    @Test
    @DisplayName("execute: 試合表が生成済みなら、早退にすると同時に未開始セットが自動で再編成される")
    void autoReplansScheduleOnSingleLeave() {
        Room room = roomWithGuests(4);
        MatchSchedule schedule = new MatchingDomainService(new Random(2L))
                .generate(room.id(), room.participants().stream().map(p -> p.id()).toList(), 1, 5);
        matchScheduleRepository.save(schedule);
        ParticipantId leaving = room.participants().get(0).id();

        useCase.execute(room.id(), leaving);

        MatchSchedule after = matchScheduleRepository.findByRoomId(room.id()).orElseThrow();
        // 早退にした人は、まだ始まっていない(=そもそも1つも開始していない)セットの
        // どこにも含まれない。手動で「再編成」を別途呼んでいないのに、ここまで反映される
        // ことが今回のポイント。
        assertThat(participantIdsInMatches(after)).doesNotContain(leaving);
    }

    @Test
    @DisplayName("executeMany: 試合表が生成済みなら、まとめて早退にしたときも未開始セットが自動で再編成される")
    void autoReplansScheduleOnBulkLeave() {
        Room room = roomWithGuests(5);
        MatchSchedule schedule = new MatchingDomainService(new Random(3L))
                .generate(room.id(), room.participants().stream().map(p -> p.id()).toList(), 1, 5);
        matchScheduleRepository.save(schedule);
        List<ParticipantId> leaving =
                List.of(room.participants().get(0).id(), room.participants().get(1).id());

        useCase.executeMany(room.id(), leaving);

        MatchSchedule after = matchScheduleRepository.findByRoomId(room.id()).orElseThrow();
        assertThat(participantIdsInMatches(after)).doesNotContainAnyElementsOf(leaving);
    }

    @Test
    @DisplayName("execute: 試合表がまだ無いルームでは、再編成をせずに早退だけ反映する")
    void skipsReplanWhenScheduleNotGeneratedYet() {
        Room room = roomWithGuests(4);
        ParticipantId leaving = room.participants().get(0).id();

        Room result = useCase.execute(room.id(), leaving);

        assertThat(result.participants().get(0).status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(matchScheduleRepository.findByRoomId(room.id())).isEmpty();
    }

    @Test
    @DisplayName("executeMany: 途中に存在しない参加者がいれば例外で、誰も早退にならない")
    void rollsBackNothingWhenOneParticipantMissing() {
        Room room = roomWithGuests(3);
        ParticipantId valid = room.participants().get(0).id();
        ParticipantId missing = ParticipantId.newId();

        roomRepository.saveCount = 0;
        assertThatThrownBy(() -> useCase.executeMany(room.id(), List.of(valid, missing)))
                .isInstanceOf(ResourceNotFoundException.class);

        // save() まで到達していないので、有効な方(valid)も反映されていない。
        assertThat(roomRepository.saveCount).isZero();
        Room reloaded = roomRepository.findById(room.id()).orElseThrow();
        assertThat(reloaded.participants().get(0).status()).isEqualTo(ParticipantStatus.ACTIVE);
    }

    @Test
    @DisplayName("executeMany: 空リストは IllegalArgumentException")
    void rejectsEmptyList() {
        Room room = roomWithGuests(2);
        assertThatThrownBy(() -> useCase.executeMany(room.id(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.executeMany(room.id(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("executeMany: 存在しないルームは ResourceNotFoundException")
    void throwsWhenRoomMissing() {
        assertThatThrownBy(
                        () -> useCase.executeMany(RoomId.newId(), List.of(ParticipantId.newId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeRoomRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();
        int saveCount = 0;

        @Override
        public Room save(Room room) {
            saveCount++;
            store.put(room.id(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId);
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            // Room.reconstitute は参加者リストを防御的コピーするので、保存済みの Room を
            // そのまま返さずここを通すことで、呼び出し側の in-place な変更(mark系メソッドは
            // participants リストを直接書き換える)が、保存(save)前の状態に波及しないように
            // する。実際の JPA 実装は findById のたびに DB から新しい Room を組み立てるので、
            // この分離が無いと「保存していないのに変更が見える」という偽陽性/偽陰性が起きる。
            Room room = store.get(roomId);
            if (room == null) return Optional.empty();
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
