package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class ClaimNextParticipantUseCaseTest {

    private FakeRoomRepository roomRepository;
    private ClaimNextParticipantUseCase useCase;

    @BeforeEach
    void setUp() {
        roomRepository = new FakeRoomRepository();
        useCase = new ClaimNextParticipantUseCase(roomRepository);
    }

    /** かんたん作成と同じ形: 番号(1..N)の参加者だけがいるルーム。 */
    private Room quickCreatedRoom(int count) {
        Room room = Room.create(
                "テスト", OffsetDateTime.now(), null, null, 1, UserId.of(UUID.randomUUID()));
        for (int i = 1; i <= count; i++) {
            room.addGuest(String.valueOf(i));
        }
        roomRepository.save(room);
        return room;
    }

    @Test
    @DisplayName("一番若い番号の枠が自動で割り当たる")
    void claimsLowestFreeSlot() {
        Room room = quickCreatedRoom(5);

        ClaimNextParticipantUseCase.Result result = useCase.execute(room.id(), "たろう");

        Participant claimed = result.room().participants().stream()
                .filter(p -> p.id().equals(result.claimedId()))
                .findFirst().orElseThrow();
        assertThat(claimed.guestName()).isEqualTo("たろう");
        assertThat(result.room().participants().get(0).id()).isEqualTo(result.claimedId());
    }

    @Test
    @DisplayName("2回呼ぶと、それぞれ別の番号(1番→2番)が割り当たる")
    void claimsDistinctSlotsOnEachCall() {
        Room room = quickCreatedRoom(5);

        var first = useCase.execute(room.id(), "いち");
        var second = useCase.execute(room.id(), "に");

        assertThat(first.claimedId()).isNotEqualTo(second.claimedId());
        List<Participant> participants = second.room().participants();
        assertThat(participants.get(0).id()).isEqualTo(first.claimedId());
        assertThat(participants.get(1).id()).isEqualTo(second.claimedId());
    }

    @Test
    @DisplayName("名前を入れなければ「ゲスト」になる")
    void defaultsToGuestWhenNameBlank() {
        Room room = quickCreatedRoom(4);

        ClaimNextParticipantUseCase.Result result = useCase.execute(room.id(), "  ");

        Participant claimed = result.room().participants().stream()
                .filter(p -> p.id().equals(result.claimedId()))
                .findFirst().orElseThrow();
        assertThat(claimed.guestName()).isEqualTo("ゲスト");
    }

    @Test
    @DisplayName("全員名乗り済み(空き番号が無い)なら例外")
    void throwsWhenNoFreeSlot() {
        Room room = quickCreatedRoom(1);
        useCase.execute(room.id(), "うめた");

        assertThatThrownBy(() -> useCase.execute(room.id(), "だれか"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空き番号");
    }

    @Test
    @DisplayName("定員が満員でも、早退などでフリーになった枠があればそこに詰められる")
    void claimsFreedSlotWhenFull() {
        Room room = quickCreatedRoom(3);
        // 全員が名乗り、定員は満員になる。
        for (int i = 1; i <= 3; i++) {
            useCase.execute(room.id(), "person" + i);
        }
        // 2番が早退してフリーになった、という状況を再現する。
        Participant two = room.participants().get(1);
        room.renameParticipant(two.id(), Participant.FREE_SLOT);
        roomRepository.save(room);

        ClaimNextParticipantUseCase.Result result = useCase.execute(room.id(), "あとから");

        assertThat(result.claimedId()).isEqualTo(two.id());
        Participant claimed = result.room().participants().stream()
                .filter(p -> p.id().equals(result.claimedId()))
                .findFirst().orElseThrow();
        assertThat(claimed.guestName()).isEqualTo("あとから");
    }

    @Test
    @DisplayName("運営者が代理追加した遅刻者・ビジター枠にも詰められる")
    void claimsVisitorPlaceholderSlot() {
        Room room = quickCreatedRoom(2);
        useCase.execute(room.id(), "person1");
        useCase.execute(room.id(), "person2");
        // 運営者が3人目の遅刻者ぶんを代理追加した、という状況を再現する。
        Participant added = room.addGuest(Participant.VISITOR_PLACEHOLDER);
        roomRepository.save(room);

        ClaimNextParticipantUseCase.Result result = useCase.execute(room.id(), "ちこく");

        assertThat(result.claimedId()).isEqualTo(added.id());
    }

    @Test
    @DisplayName("終了したセッションには参加できない")
    void throwsWhenClosed() {
        Room room = quickCreatedRoom(4);
        room.close();
        roomRepository.save(room);

        assertThatThrownBy(() -> useCase.execute(room.id(), "だれか"))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- インメモリ実装 ---

    private static final class FakeRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> store = new HashMap<>();

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
        public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return store.values().stream()
                    .filter(r -> status == null || r.status() == status)
                    .toList();
        }
    }
}
