package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.ParticipantStatus;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.SessionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipantAvailabilityUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private MarkParticipantLeftUseCase markLeft;
    private ReactivateParticipantUseCase reactivate;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        markLeft = new MarkParticipantLeftUseCase(sessionRepository);
        reactivate = new ReactivateParticipantUseCase(sessionRepository);
    }

    private Session sessionWithGuest() {
        Session session = Session.create(
                CircleId.of(UUID.randomUUID()), "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        session.addGuest("ゲスト");
        sessionRepository.save(session);
        return session;
    }

    @Test
    @DisplayName("早退で LEFT になり、活動対象から外れる。復帰で ACTIVE に戻る")
    void marksLeftAndReactivates() {
        Session session = sessionWithGuest();
        ParticipantId pid = session.participants().get(0).id();

        Session afterLeave = markLeft.execute(session.id(), pid);
        assertThat(afterLeave.participants().get(0).status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(afterLeave.activeParticipantIds()).isEmpty();

        Session afterBack = reactivate.execute(session.id(), pid);
        assertThat(afterBack.participants().get(0).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(afterBack.activeParticipantIds()).containsExactly(pid);
    }

    @Test
    @DisplayName("存在しない参加者は ResourceNotFoundException")
    void throwsWhenParticipantMissing() {
        Session session = sessionWithGuest();
        assertThatThrownBy(() -> markLeft.execute(session.id(), ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("存在しないセッションは ResourceNotFoundException")
    void throwsWhenSessionMissing() {
        assertThatThrownBy(() -> markLeft.execute(SessionId.newId(), ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

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
}
