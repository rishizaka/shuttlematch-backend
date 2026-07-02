package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
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

    private FakeSessionRepository sessionRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private QuickCreateSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        useCase = new QuickCreateSessionUseCase(
                sessionRepository, matchScheduleRepository, new MatchingDomainService(new Random(1L)));
    }

    private QuickCreateSessionCommand cmd(int courts, int participants) {
        return new QuickCreateSessionCommand(
                CircleId.of(UUID.randomUUID()), "7/2 夜練", courts, participants,
                UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("番号の参加者を登録し、試合表まで生成して GENERATED になる")
    void createsNumberedParticipantsAndSchedule() {
        Session session = useCase.execute(cmd(1, 6));

        assertThat(session.status()).isEqualTo(SessionStatus.GENERATED);
        assertThat(session.participants()).hasSize(6);
        assertThat(session.participants().stream().map(p -> p.guestName()).toList())
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(matchScheduleRepository.findBySessionId(session.id())).isPresent();
    }

    @Test
    @DisplayName("参加人数がコートに満たない場合は例外(保存されない)")
    void throwsWhenNotEnoughForCourts() {
        assertThatThrownBy(() -> useCase.execute(cmd(2, 5))) // 2コート=8人必要
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- インメモリ実装 ---

    private static final class FakeSessionRepository implements SessionRepository {
        private final java.util.Map<SessionId, Session> store = new java.util.HashMap<>();

        @Override
        public Session save(Session session) {
            store.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override
        public List<Session> findByStatus(SessionStatus status) {
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
        public Optional<MatchSchedule> findBySessionId(SessionId sessionId) {
            return store.stream().filter(s -> s.sessionId().equals(sessionId))
                    .reduce((a, b) -> b);
        }

        @Override
        public void deleteBySessionId(SessionId sessionId) {
            store.removeIf(s -> s.sessionId().equals(sessionId));
        }

        @Override
        public Optional<MatchSchedule> startSet(
                SessionId sessionId, int setNumber, OffsetDateTime startedAt) {
            return findBySessionId(sessionId);
        }
    }
}
