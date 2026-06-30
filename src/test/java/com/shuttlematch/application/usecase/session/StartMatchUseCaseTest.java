package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
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

class StartMatchUseCaseTest {

    private FakeRepo repo;
    private StartMatchUseCase useCase;
    private final SessionId sessionId = SessionId.newId();

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        useCase = new StartMatchUseCase(repo, Clock.fixed(Instant.parse("2026-06-30T10:00:00Z"), ZoneOffset.UTC));
    }

    private Match match(int n, OffsetDateTime startedAt) {
        Pair a = new Pair(ParticipantId.newId(), ParticipantId.newId());
        Pair b = new Pair(ParticipantId.newId(), ParticipantId.newId());
        // 1コート想定: 試合番号 n をそのままセット番号として扱う
        return new Match(MatchNumber.of(n), n, 1, a, b, startedAt);
    }

    private void seed(List<Match> matches) {
        repo.schedule = new MatchSchedule(sessionId, matches);
    }

    @Test
    @DisplayName("最初は第1試合だけ開始できる")
    void startsFirstMatch() {
        seed(List.of(match(1, null), match(2, null), match(3, null)));
        MatchSchedule result = useCase.execute(sessionId, 1);
        assertThat(result.matches().get(0).isStarted()).isTrue();
    }

    @Test
    @DisplayName("第1未開始で第2を開始しようとすると IllegalStateException")
    void cannotSkipAhead() {
        seed(List.of(match(1, null), match(2, null), match(3, null)));
        assertThatThrownBy(() -> useCase.execute(sessionId, 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("第1開始済みなら第2を開始できる")
    void startsNextInOrder() {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        seed(List.of(match(1, t1), match(2, null), match(3, null)));
        MatchSchedule result = useCase.execute(sessionId, 2);
        assertThat(result.matches().get(1).isStarted()).isTrue();
    }

    @Test
    @DisplayName("既に開始済みの試合は再開始できない(順番外)")
    void cannotRestartStarted() {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        seed(List.of(match(1, t1), match(2, null)));
        assertThatThrownBy(() -> useCase.execute(sessionId, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("スケジュールが無ければ ResourceNotFoundException")
    void noSchedule() {
        assertThatThrownBy(() -> useCase.execute(sessionId, 1))
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
        public Optional<MatchSchedule> findBySessionId(SessionId id) {
            return Optional.ofNullable(schedule);
        }

        @Override
        public void deleteBySessionId(SessionId id) {
            schedule = null;
        }

        @Override
        public Optional<MatchSchedule> startMatch(SessionId id, int matchNumber, OffsetDateTime startedAt) {
            if (schedule == null) {
                return Optional.empty();
            }
            List<Match> updated = new ArrayList<>();
            for (Match m : schedule.matches()) {
                updated.add(m.matchNumber().value() == matchNumber ? m.withStartedAt(startedAt) : m);
            }
            schedule = new MatchSchedule(id, updated);
            return Optional.of(schedule);
        }
    }
}
