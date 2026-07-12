package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.ParticipantStatus;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipantAvailabilityUseCaseTest {

    private FakeSessionRepository roomRepository;
    private MarkParticipantLeftUseCase markLeft;
    private ReactivateParticipantUseCase reactivate;
    private RenameParticipantUseCase rename;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeSessionRepository();
        markLeft = new MarkParticipantLeftUseCase(roomRepository);
        reactivate = new ReactivateParticipantUseCase(roomRepository);
        rename = new RenameParticipantUseCase(roomRepository);
    }

    private Room sessionWithGuest() {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID()));
        room.addGuest("ゲスト");
        roomRepository.save(room);
        return room;
    }

    @Test
    @DisplayName("早退で LEFT になり、活動対象から外れる。復帰で ACTIVE に戻る")
    void marksLeftAndReactivates() {
        Room room = sessionWithGuest();
        ParticipantId pid = room.participants().get(0).id();

        Room afterLeave = markLeft.execute(room.id(), pid);
        assertThat(afterLeave.participants().get(0).status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(afterLeave.activeParticipantIds()).isEmpty();

        Room afterBack = reactivate.execute(room.id(), pid);
        assertThat(afterBack.participants().get(0).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(afterBack.activeParticipantIds()).containsExactly(pid);
    }

    @Test
    @DisplayName("rename で番号参加者に名前を付けられる")
    void renamesParticipant() {
        Room room = sessionWithGuest();
        var pid = room.participants().get(0).id();

        Room result = rename.execute(room.id(), pid, "太郎");

        assertThat(result.participants().get(0).guestName()).isEqualTo("太郎");
    }

    @Test
    @DisplayName("rename で空名は例外")
    void renameRejectsBlank() {
        Room room = sessionWithGuest();
        var pid = room.participants().get(0).id();
        assertThatThrownBy(() -> rename.execute(room.id(), pid, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("存在しない参加者は ResourceNotFoundException")
    void throwsWhenParticipantMissing() {
        Room room = sessionWithGuest();
        assertThatThrownBy(() -> markLeft.execute(room.id(), ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("存在しないセッションは ResourceNotFoundException")
    void throwsWhenSessionMissing() {
        assertThatThrownBy(() -> markLeft.execute(RoomId.newId(), ParticipantId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeSessionRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();

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

        @Override
        public int countCreatedSince(com.shuttlematch.domain.model.user.UserId createdBy, java.time.OffsetDateTime since) {
            return 0;
        }

        @Override
        public java.util.List<Room> findNotClosedCreatedBefore(java.time.OffsetDateTime createdBefore) {
            return java.util.List.of();
        }

        @Override
        public List<Room> search(RoomStatus status, java.time.OffsetDateTime heldFrom, java.time.OffsetDateTime heldTo) {
            return store.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .filter(r -> heldFrom == null || !r.heldAt().isBefore(heldFrom))
                    .filter(r -> heldTo == null || r.heldAt().isBefore(heldTo))
                    .toList();
        }
    }
}
