package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.Participant;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.session.SessionVisibility;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.SessionRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionUseCaseTest {

    private FakeSessionRepository sessionRepository;
    private CreateSessionUseCase createSessionUseCase;
    private AddParticipantUseCase addParticipantUseCase;
    private RemoveParticipantUseCase removeParticipantUseCase;
    private GetSessionUseCase getSessionUseCase;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        createSessionUseCase = new CreateSessionUseCase(sessionRepository);
        addParticipantUseCase = new AddParticipantUseCase(sessionRepository);
        removeParticipantUseCase = new RemoveParticipantUseCase(sessionRepository);
        getSessionUseCase = new GetSessionUseCase(sessionRepository);
    }

    private CreateSessionCommand createCommand() {
        return new CreateSessionCommand(
                CircleId.of(UUID.randomUUID()), "練習会", OffsetDateTime.now(),
                "体育館", null, 2, SessionVisibility.PUBLIC, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("セッションを作成して保存する")
    void createsSession() {
        Session created = createSessionUseCase.execute(createCommand());
        assertThat(sessionRepository.findById(created.id())).isPresent();
    }

    @Test
    @DisplayName("ゲストを参加登録できる")
    void addsGuestParticipant() {
        Session session = createSessionUseCase.execute(createCommand());

        Session updated = addParticipantUseCase.execute(
                new AddParticipantCommand(session.id(), null, "ゲストA"));

        assertThat(updated.participants()).hasSize(1);
        assertThat(updated.participants().get(0).isGuest()).isTrue();
    }

    @Test
    @DisplayName("登録ユーザーを参加登録できる")
    void addsUserParticipant() {
        Session session = createSessionUseCase.execute(createCommand());
        UserId user = UserId.of(UUID.randomUUID());

        Session updated = addParticipantUseCase.execute(
                new AddParticipantCommand(session.id(), user, null));

        assertThat(updated.participants()).hasSize(1);
        assertThat(updated.participants().get(0).userId()).isEqualTo(user);
    }

    @Test
    @DisplayName("存在しないセッションへの参加登録は ResourceNotFoundException")
    void addToMissingSessionThrows() {
        assertThatThrownBy(() -> addParticipantUseCase.execute(
                new AddParticipantCommand(SessionId.newId(), null, "ゲスト")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("userId と guestName の両方指定はコマンド生成時に弾く")
    void rejectsBothUserAndGuest() {
        assertThatThrownBy(() -> new AddParticipantCommand(
                SessionId.newId(), UserId.of(UUID.randomUUID()), "ゲスト"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("参加者を削除できる")
    void removesParticipant() {
        Session session = createSessionUseCase.execute(createCommand());
        Session withGuest = addParticipantUseCase.execute(
                new AddParticipantCommand(session.id(), null, "ゲスト"));
        Participant participant = withGuest.participants().get(0);

        removeParticipantUseCase.execute(session.id(), participant.id());

        assertThat(getSessionUseCase.execute(session.id()).participants()).isEmpty();
    }

    @Test
    @DisplayName("存在しない参加者の削除は ResourceNotFoundException")
    void removeMissingParticipantThrows() {
        Session session = createSessionUseCase.execute(createCommand());
        assertThatThrownBy(() -> removeParticipantUseCase.execute(
                session.id(), com.shuttlematch.domain.model.session.ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("存在しないセッションの取得は ResourceNotFoundException")
    void getMissingSessionThrows() {
        assertThatThrownBy(() -> getSessionUseCase.execute(SessionId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeSessionRepository implements SessionRepository {
        private final Map<SessionId, Session> store = new HashMap<>();

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
