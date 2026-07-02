package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomUseCaseTest {

    private FakeSessionRepository roomRepository;
    private CreateRoomUseCase createSessionUseCase;
    private AddParticipantUseCase addParticipantUseCase;
    private RemoveParticipantUseCase removeParticipantUseCase;
    private GetRoomUseCase getRoomUseCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        createSessionUseCase = new CreateRoomUseCase(roomRepository);
        addParticipantUseCase = new AddParticipantUseCase(roomRepository);
        removeParticipantUseCase = new RemoveParticipantUseCase(roomRepository);
        getRoomUseCase = new GetRoomUseCase(roomRepository);
    }

    private CreateRoomCommand createCommand() {
        return new CreateRoomCommand(
                "練習会", OffsetDateTime.now(),
                "体育館", null, 2, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("セッションを作成して保存する")
    void createsSession() {
        Room created = createSessionUseCase.execute(createCommand());
        assertThat(roomRepository.findById(created.id())).isPresent();
    }

    @Test
    @DisplayName("ゲストを参加登録できる")
    void addsGuestParticipant() {
        Room room = createSessionUseCase.execute(createCommand());

        Room updated = addParticipantUseCase.execute(
                new AddParticipantCommand(room.id(), null, "ゲストA"));

        assertThat(updated.participants()).hasSize(1);
        assertThat(updated.participants().get(0).isGuest()).isTrue();
    }

    @Test
    @DisplayName("登録ユーザーを参加登録できる")
    void addsUserParticipant() {
        Room room = createSessionUseCase.execute(createCommand());
        UserId user = UserId.of(UUID.randomUUID());

        Room updated = addParticipantUseCase.execute(
                new AddParticipantCommand(room.id(), user, null));

        assertThat(updated.participants()).hasSize(1);
        assertThat(updated.participants().get(0).userId()).isEqualTo(user);
    }

    @Test
    @DisplayName("存在しないセッションへの参加登録は ResourceNotFoundException")
    void addToMissingSessionThrows() {
        assertThatThrownBy(() -> addParticipantUseCase.execute(
                new AddParticipantCommand(RoomId.newId(), null, "ゲスト")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("userId と guestName の両方指定はコマンド生成時に弾く")
    void rejectsBothUserAndGuest() {
        assertThatThrownBy(() -> new AddParticipantCommand(
                RoomId.newId(), UserId.of(UUID.randomUUID()), "ゲスト"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("参加者を削除できる")
    void removesParticipant() {
        Room room = createSessionUseCase.execute(createCommand());
        Room withGuest = addParticipantUseCase.execute(
                new AddParticipantCommand(room.id(), null, "ゲスト"));
        Participant participant = withGuest.participants().get(0);

        removeParticipantUseCase.execute(room.id(), participant.id());

        assertThat(getRoomUseCase.execute(room.id()).participants()).isEmpty();
    }

    @Test
    @DisplayName("存在しない参加者の削除は ResourceNotFoundException")
    void removeMissingParticipantThrows() {
        Room room = createSessionUseCase.execute(createCommand());
        assertThatThrownBy(() -> removeParticipantUseCase.execute(
                room.id(), com.shuttlematch.domain.model.room.ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("存在しないセッションの取得は ResourceNotFoundException")
    void getMissingSessionThrows() {
        assertThatThrownBy(() -> getRoomUseCase.execute(RoomId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeSessionRepository implements RoomRepository {
        private final Map<RoomId, Room> store = new HashMap<>();

        @Override
        public Room save(Room room) {
            store.put(room.id(), room);
            return room;
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId));
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(s -> s.status() == status).toList();
        }
    }
}
