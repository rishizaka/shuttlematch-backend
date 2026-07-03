package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class QuickCreateSessionUseCaseTest {

    private FakeSessionRepository roomRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private QuickCreateRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        useCase = new QuickCreateRoomUseCase(
                roomRepository, matchScheduleRepository, new MatchingDomainService(new Random(1L)));
    }

    private QuickCreateRoomCommand cmd(int courts, int participants) {
        return new QuickCreateRoomCommand(
                "7/2 夜練", courts, participants,
                UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("番号の参加者を登録し、試合表まで生成して GENERATED になる")
    void createsNumberedParticipantsAndSchedule() {
        Room room = useCase.execute(cmd(1, 6));

        assertThat(room.status()).isEqualTo(RoomStatus.GENERATED);
        assertThat(room.participants()).hasSize(6);
        assertThat(room.participants().stream().map(p -> p.guestName()).toList())
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(matchScheduleRepository.findByRoomId(room.id())).isPresent();
    }

    @Test
    @DisplayName("参加人数がコートに満たない場合は例外(保存されない)")
    void throwsWhenNotEnoughForCourts() {
        assertThatThrownBy(() -> useCase.execute(cmd(2, 5))) // 2コート=8人必要
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同一ユーザーが当日 3 件作成済みなら例外(4件目は作れない)")
    void throwsWhenDailyLimitReached() {
        roomRepository.createdTodayCount = Room.MAX_ROOMS_PER_USER_PER_DAY;

        assertThatThrownBy(() -> useCase.execute(cmd(1, 4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("当日 2 件作成済みなら 3 件目は作れる")
    void allowsWhenUnderDailyLimit() {
        roomRepository.createdTodayCount = Room.MAX_ROOMS_PER_USER_PER_DAY - 1;

        Room room = useCase.execute(cmd(1, 4));

        assertThat(room.status()).isEqualTo(RoomStatus.GENERATED);
    }

    // --- インメモリ実装 ---

    private static final class FakeSessionRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();
        private int createdTodayCount = 0;

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
            return createdTodayCount;
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
            return store.stream().filter(s -> s.roomId().equals(roomId))
                    .reduce((a, b) -> b);
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
