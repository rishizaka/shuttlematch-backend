package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ForbiddenOperationException;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 削除の鍵は共有コード。認証が無いなかで、終了したルーム(roomId を一覧で公開している)を
 * 一覧から順に消されないようにするための最後の砦なので、通る/通らないを固定しておく。
 */
class DeleteRoomUseCaseTest {

    private FakeRoomRepository roomRepository;
    private FakeMatchScheduleRepository scheduleRepository;
    private DeleteRoomUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        scheduleRepository = new FakeMatchScheduleRepository();
        useCase = new DeleteRoomUseCase(roomRepository, scheduleRepository);
    }

    private Room saveRoom() {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        roomRepository.save(room);
        return room;
    }

    @Test
    @DisplayName("共有コードが一致すれば試合表ごと削除する")
    void deletesWithShareCode() {
        Room room = saveRoom();

        useCase.execute(room.id(), room.shareCode());

        assertThat(roomRepository.findById(room.id())).isEmpty();
        assertThat(scheduleRepository.deleted).containsExactly(room.id());
    }

    @Test
    @DisplayName("終了したルームも共有コードがあれば削除できる")
    void deletesClosedRoom() {
        Room room = saveRoom();
        room.close();
        roomRepository.save(room);

        useCase.execute(room.id(), room.shareCode());

        assertThat(roomRepository.findById(room.id())).isEmpty();
    }

    @Test
    @DisplayName("共有コードが違えば ForbiddenOperationException で、削除もされない")
    void rejectsWrongShareCode() {
        Room room = saveRoom();

        assertThatThrownBy(() -> useCase.execute(room.id(), "wrongcode"))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(roomRepository.findById(room.id())).isPresent();
        assertThat(scheduleRepository.deleted).isEmpty();
    }

    @Test
    @DisplayName("共有コード無しでは削除できない(roomId だけでは消せない)")
    void rejectsMissingShareCode() {
        Room room = saveRoom();

        assertThatThrownBy(() -> useCase.execute(room.id(), null))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(roomRepository.findById(room.id())).isPresent();
    }

    @Test
    @DisplayName("存在しないルームは ResourceNotFoundException")
    void throwsWhenRoomMissing() {
        assertThatThrownBy(() -> useCase.execute(RoomId.newId(), "anycode"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeMatchScheduleRepository implements MatchScheduleRepository {
        final List<RoomId> deleted = new ArrayList<>();

        @Override
        public MatchSchedule save(MatchSchedule schedule) {
            return schedule;
        }

        @Override
        public Optional<MatchSchedule> findByRoomId(RoomId roomId) {
            return Optional.empty();
        }

        @Override
        public void deleteByRoomId(RoomId roomId) {
            deleted.add(roomId);
        }

        @Override
        public Optional<MatchSchedule> startSet(
                RoomId roomId, int setNumber, OffsetDateTime startedAt) {
            return Optional.empty();
        }
    }

    private static final class FakeRoomRepository implements RoomRepository {
        private final java.util.Map<RoomId, Room> store = new java.util.HashMap<>();

        @Override
        public Room save(Room room) {
            store.put(room.id(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId);
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId));
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
        public List<Room> search(
                RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return List.copyOf(store.values());
        }
    }
}
