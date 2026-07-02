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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StartSetUseCaseTest {

    private FakeRepo repo;
    private StartSetUseCase useCase;
    private final RoomId roomId = RoomId.newId();

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        useCase = new StartSetUseCase(repo, Clock.fixed(Instant.parse("2026-06-30T10:00:00Z"), ZoneOffset.UTC));
    }

    /** 1コート想定: 試合番号 n をそのままセット番号として扱う。 */
    private Match match(int n, OffsetDateTime startedAt) {
        return match(n, n, 1, startedAt);
    }

    private Match match(int matchNumber, int setNumber, int court, OffsetDateTime startedAt) {
        Pair a = new Pair(ParticipantId.newId(), ParticipantId.newId());
        Pair b = new Pair(ParticipantId.newId(), ParticipantId.newId());
        return new Match(MatchNumber.of(matchNumber), setNumber, court, a, b, startedAt);
    }

    private void seed(List<Match> matches) {
        repo.schedule = new MatchSchedule(roomId, matches);
    }

    @Test
    @DisplayName("最初は第1セットだけ開始できる")
    void startsFirstSet() {
        seed(List.of(match(1, null), match(2, null), match(3, null)));
        MatchSchedule result = useCase.execute(roomId, 1);
        assertThat(result.matches().get(0).isStarted()).isTrue();
    }

    @Test
    @DisplayName("第1未開始で第2を開始しようとすると IllegalStateException")
    void cannotSkipAhead() {
        seed(List.of(match(1, null), match(2, null), match(3, null)));
        assertThatThrownBy(() -> useCase.execute(roomId, 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("第1開始済みなら第2を開始できる")
    void startsNextInOrder() {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        seed(List.of(match(1, t1), match(2, null), match(3, null)));
        MatchSchedule result = useCase.execute(roomId, 2);
        assertThat(result.matches().get(1).isStarted()).isTrue();
    }

    @Test
    @DisplayName("既に開始済みのセットは再開始できない(順番外)")
    void cannotRestartStarted() {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        seed(List.of(match(1, t1), match(2, null)));
        assertThatThrownBy(() -> useCase.execute(roomId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("複数コート: セット開始でそのセットの全コートに開始時刻が付く")
    void startsAllCourtsInSet() {
        // 第1セット: 試合1(コート1)/試合2(コート2)、第2セット: 試合3/試合4
        seed(List.of(
                match(1, 1, 1, null), match(2, 1, 2, null),
                match(3, 2, 1, null), match(4, 2, 2, null)));

        MatchSchedule result = useCase.execute(roomId, 1);

        assertThat(result.matches().stream().filter(m -> m.setNumber() == 1))
                .allMatch(Match::isStarted);
        assertThat(result.matches().stream().filter(m -> m.setNumber() == 2))
                .noneMatch(Match::isStarted);
    }

    @Test
    @DisplayName("スケジュールが無ければ ResourceNotFoundException")
    void noSchedule() {
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
            boolean matched = false;
            for (Match m : schedule.matches()) {
                if (m.setNumber() == setNumber) {
                    updated.add(m.withStartedAt(startedAt));
                    matched = true;
                } else {
                    updated.add(m);
                }
            }
            if (!matched) {
                return Optional.empty();
            }
            schedule = new MatchSchedule(id, updated);
            return Optional.of(schedule);
        }
    }
}
