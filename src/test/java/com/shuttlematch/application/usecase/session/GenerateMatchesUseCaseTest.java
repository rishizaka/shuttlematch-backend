package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
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

class GenerateMatchesUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private GenerateMatchesUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        useCase = new GenerateMatchesUseCase(
                sessionRepository,
                matchScheduleRepository,
                new MatchingDomainService(new Random(100L)));
    }

    private Session openSessionWithGuests(int count) {
        Session session = Session.create(
                CircleId.of(UUID.randomUUID()), "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            session.addGuest("ゲスト" + i);
        }
        sessionRepository.save(session);
        return session;
    }

    @Test
    @DisplayName("セッションの参加者から生成し、保存して返す。セッションは生成済みになる")
    void generatesAndPersistsSchedule() {
        Session session = openSessionWithGuests(8);

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(session.id()));

        assertThat(result.size()).isEqualTo(15);
        assertThat(matchScheduleRepository.findBySessionId(session.id())).contains(result);
        assertThat(sessionRepository.findById(session.id()).orElseThrow().status())
                .isEqualTo(SessionStatus.GENERATED);
    }

    @Test
    @DisplayName("試合数を指定して生成できる")
    void generatesWithCustomMatchCount() {
        Session session = openSessionWithGuests(6);

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(session.id(), 5));

        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("再生成時は既存スケジュールを削除してから保存する")
    void regenerationDeletesExistingSchedule() {
        Session session = openSessionWithGuests(8);

        useCase.execute(new GenerateMatchesCommand(session.id()));
        useCase.execute(new GenerateMatchesCommand(session.id()));

        assertThat(matchScheduleRepository.deleteCount).isEqualTo(2);
        assertThat(matchScheduleRepository.count(session.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("参加者が4人未満なら例外を投げ、保存しない")
    void doesNotPersistWhenTooFewParticipants() {
        Session session = openSessionWithGuests(3);

        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(session.id())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(matchScheduleRepository.findBySessionId(session.id())).isEmpty();
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(SessionId.newId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("終了済みセッションでは生成できない(IllegalStateException)")
    void throwsWhenSessionClosed() {
        Session closed = Session.reconstitute(
                SessionId.newId(), CircleId.of(UUID.randomUUID()), "終了", OffsetDateTime.now(),
                null, null, SessionStatus.CLOSED, UserId.of(UUID.randomUUID()), List.of());
        sessionRepository.save(closed);

        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(closed.id())))
                .isInstanceOf(IllegalStateException.class);
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
    }

    private static final class FakeMatchScheduleRepository implements MatchScheduleRepository {
        private final List<MatchSchedule> store = new ArrayList<>();
        int deleteCount = 0;

        @Override
        public MatchSchedule save(MatchSchedule schedule) {
            store.add(schedule);
            return schedule;
        }

        @Override
        public Optional<MatchSchedule> findBySessionId(SessionId sessionId) {
            return store.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .reduce((first, second) -> second);
        }

        @Override
        public void deleteBySessionId(SessionId sessionId) {
            deleteCount++;
            store.removeIf(s -> s.sessionId().equals(sessionId));
        }

        long count(SessionId sessionId) {
            return store.stream().filter(s -> s.sessionId().equals(sessionId)).count();
        }
    }
}
