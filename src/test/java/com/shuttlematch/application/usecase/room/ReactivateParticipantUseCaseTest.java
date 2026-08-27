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

/**
 * executeMany() は「1回の読み込み・1回の保存」であることが要点(MarkParticipantLeftUseCase の
 * javadoc の lost update 対策と同じ)。ここでは save() の呼び出し回数と、
 * 失敗時に何も保存されないことを固定する。
 */
class ReactivateParticipantUseCaseTest {

    private FakeRoomRepository roomRepository;
    private ReactivateParticipantUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        useCase = new ReactivateParticipantUseCase(roomRepository);
    }

    /** count 人ぶんゲストを追加し、うち先頭 leftCount 人を早退にした Room を返す。 */
    private Room roomWithLeftGuests(int count, int leftCount) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        for (int i = 0; i < count; i++) {
            room.addGuest(String.valueOf(i + 1));
        }
        for (int i = 0; i < leftCount; i++) {
            room.markParticipantLeft(room.participants().get(i).id());
        }
        roomRepository.save(room);
        return room;
    }

    @Test
    @DisplayName("executeMany: 指定した早退者を全員在席に戻し、保存は1回だけ")
    void reactivatesAllListedInOneSave() {
        Room room = roomWithLeftGuests(4, 3); // 0,1,2番が早退中、3番は在席
        List<ParticipantId> targets =
                List.of(room.participants().get(0).id(), room.participants().get(2).id());

        roomRepository.saveCount = 0;
        Room result = useCase.executeMany(room.id(), targets);

        assertThat(result.participants().get(0).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(result.participants().get(2).status()).isEqualTo(ParticipantStatus.ACTIVE);
        // 対象外(1番)は早退のまま。
        assertThat(result.participants().get(1).status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(result.participants().get(3).status()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(roomRepository.saveCount).isEqualTo(1);
    }

    @Test
    @DisplayName("executeMany: 途中に存在しない参加者がいれば例外で、誰も在席に戻らない")
    void rollsBackNothingWhenOneParticipantMissing() {
        Room room = roomWithLeftGuests(3, 2);
        ParticipantId valid = room.participants().get(0).id();
        ParticipantId missing = ParticipantId.newId();

        roomRepository.saveCount = 0;
        assertThatThrownBy(() -> useCase.executeMany(room.id(), List.of(valid, missing)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(roomRepository.saveCount).isZero();
        Room reloaded = roomRepository.findById(room.id()).orElseThrow();
        assertThat(reloaded.participants().get(0).status()).isEqualTo(ParticipantStatus.LEFT);
    }

    @Test
    @DisplayName("executeMany: 空リストは IllegalArgumentException")
    void rejectsEmptyList() {
        Room room = roomWithLeftGuests(2, 1);
        assertThatThrownBy(() -> useCase.executeMany(room.id(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.executeMany(room.id(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("executeMany: 存在しないルームは ResourceNotFoundException")
    void throwsWhenRoomMissing() {
        assertThatThrownBy(
                        () -> useCase.executeMany(RoomId.newId(), List.of(ParticipantId.newId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeRoomRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();
        int saveCount = 0;

        @Override
        public Room save(Room room) {
            saveCount++;
            store.put(room.id(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId);
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            // MarkParticipantLeftUseCaseTest の同名フェイクと同じ理由で防御的コピーする。
            Room room = store.get(roomId);
            if (room == null) return Optional.empty();
            return Optional.of(Room.reconstitute(
                    room.id(), room.shareCode(), room.title(), room.heldAt(), room.location(),
                    room.capacity(), room.courtCount(), room.status(), room.createdBy(),
                    room.participants(), room.fixedPairs()));
        }

        @Override
        public Optional<Room> findByShareCode(String shareCode) {
            return store.values().stream()
                    .filter(r -> shareCode.equals(r.shareCode()))
                    .findFirst();
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(r -> r.status() == status).toList();
        }

        @Override
        public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
            return 0;
        }

        @Override
        public List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore) {
            return List.of();
        }

        @Override
        public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return List.copyOf(store.values());
        }
    }
}
