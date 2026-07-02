package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.CircleId;
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

class CloseSessionUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private CloseSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        useCase = new CloseSessionUseCase(sessionRepository);
    }

    @Test
    @DisplayName("セッションを終了済みにして返す")
    void closesSession() {
        Session session = Session.create(
                CircleId.of(UUID.randomUUID()), "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        sessionRepository.save(session);

        Session result = useCase.execute(session.id());

        assertThat(result.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(sessionRepository.findById(session.id()).orElseThrow().status())
                .isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    @DisplayName("存在しないセッションは ResourceNotFoundException")
    void throwsWhenSessionMissing() {
        assertThatThrownBy(() -> useCase.execute(SessionId.newId()))
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
