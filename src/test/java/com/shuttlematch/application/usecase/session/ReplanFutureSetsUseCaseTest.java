package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.ParticipantId;
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

class ReplanFutureSetsUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private MatchingDomainService matchingDomainService;
    private ReplanFutureSetsUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        matchingDomainService = new MatchingDomainService(new Random(200L));
        useCase = new ReplanFutureSetsUseCase(
                sessionRepository, matchScheduleRepository, matchingDomainService);
    }

    private Session openSessionWithGuests(int count) {
        Session session = Session.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            session.addGuest("ゲスト" + i);
        }
        session.markGenerated();
        sessionRepository.save(session);
        return session;
    }

    /** 第1..upTo セットを開始済みにしたスケジュールを保存する。 */
    private void saveScheduleWithStarted(Session session, int totalSets, int upTo) {
        MatchSchedule base =
                matchingDomainService.generate(session.id(), session.activeParticipantIds(), 1, totalSets);
        OffsetDateTime t = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        List<Match> started = base.matches().stream()
                .map(m -> m.setNumber() <= upTo ? m.withStartedAt(t.plusMinutes(m.setNumber())) : m)
                .toList();
        matchScheduleRepository.save(new MatchSchedule(session.id(), started));
    }

    @Test
    @DisplayName("早退者を除外して未開始セットを再編成し、合計セット数は維持する")
    void replansFutureExcludingLeaver() {
        Session session = openSessionWithGuests(6);
        saveScheduleWithStarted(session, 5, 2);

        // 参加者の1人を早退にする
        ParticipantId leaver = session.participants().get(0).id();
        session.markParticipantLeft(leaver);
        sessionRepository.save(session);

        MatchSchedule result = useCase.execute(session.id());

        assertThat(result.setCount()).isEqualTo(5);
        boolean leaverInFuture = result.matches().stream()
                .filter(m -> m.setNumber() > 2)
                .anyMatch(m -> containsParticipant(m, leaver));
        assertThat(leaverInFuture).isFalse();
        // 保存は置き換え(削除→保存)で1件
        assertThat(matchScheduleRepository.count(session.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("スケジュール未生成なら ResourceNotFoundException")
    void throwsWhenScheduleNotGenerated() {
        Session session = openSessionWithGuests(6);
        assertThatThrownBy(() -> useCase.execute(session.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("セッションが存在しなければ ResourceNotFoundException")
    void throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(SessionId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private boolean containsParticipant(Match m, ParticipantId p) {
        return m.pairA().player1().equals(p) || m.pairA().player2().equals(p)
                || m.pairB().player1().equals(p) || m.pairB().player2().equals(p);
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
