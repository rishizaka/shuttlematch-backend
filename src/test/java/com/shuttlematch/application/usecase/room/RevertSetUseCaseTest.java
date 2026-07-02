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
    private RevertSetUseCase useCase;
    private final RoomId roomId = RoomId.newId();

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        useCase = new RevertSetUseCase(repo);
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
