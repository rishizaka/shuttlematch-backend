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

class AddSetsUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private MatchingDomainService matchingDomainService;
    private AddSetsUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        matchingDomainService = new MatchingDomainService(new Random(100L));
        useCase = new AddSetsUseCase(
                sessionRepository, matchScheduleRepository, matchingDomainService);
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
    @DisplayName("既存スケジュールにセットを追加し、保存して返す")
    void addsSetsToExistingSchedule() {
        Session session = openSessionWithGuests(6);
        // 既存: 1コート×3セット = 3試合
        MatchSchedule base = matchingDomainService.generate(session.id(), session.participantIds(), 1, 3);
        matchScheduleRepository.save(base);

        MatchSchedule result = useCase.execute(session.id(), 2);

        assertThat(result.setCount()).isEqualTo(5);
        assertThat(result.size()).isEqualTo(5);
        assertThat(matchScheduleRepository.findBySessionId(session.id())).contains(result);
        // 追加はあくまで置き換え保存(削除→保存)なので1件だけ残る
        assertThat(matchScheduleRepository.count(session.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("スケジュール未生成なら ResourceNotFoundException")
    void throwsWhenScheduleNotGenerated() {
        Session session = openSessionWithGuests(6);

        assertThatThrownBy(() -> useCase.execute(session.id(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(SessionId.newId(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
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
            return store.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .reduce((first, second) -> second);
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

        long count(SessionId sessionId) {
            return store.stream().filter(s -> s.sessionId().equals(sessionId)).count();
        }
    }
}
