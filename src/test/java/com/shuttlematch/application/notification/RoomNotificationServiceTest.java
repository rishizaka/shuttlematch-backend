package com.shuttlematch.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomNotificationServiceTest {

    private FakeSubscriptions subscriptions;
    private FakeRooms rooms;
    private FakeSchedules schedules;
    private RecordingSender sender;
    private RoomNotificationService service;

    private Room room;
    private List<ParticipantId> players;

    @BeforeEach
    void setUp() {
        subscriptions = new FakeSubscriptions();
        rooms = new FakeRooms();
        schedules = new FakeSchedules();
        sender = new RecordingSender();
        service = new RoomNotificationService(subscriptions, rooms, schedules, sender);

        room = Room.create("金曜練", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        rooms.save(room);

        // 8 名・2 コート。第1セットはコート1に 0..3、コート2に 4..7 が出る。
        players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            players.add(ParticipantId.newId());
        }
        schedules.save(new MatchSchedule(room.id(), List.of(
                Match.of(MatchNumber.of(1), 1, 1,
                        new Pair(players.get(0), players.get(1)),
                        new Pair(players.get(2), players.get(3))),
                Match.of(MatchNumber.of(2), 1, 2,
                        new Pair(players.get(4), players.get(5)),
                        new Pair(players.get(6), players.get(7))))));
    }

    private void subscribe(String token, ParticipantId participantId) {
        subscriptions.save(new PushSubscription(
                room.id(),
                ExpoPushToken.of(token),
                Optional.ofNullable(participantId),
                PushPlatform.ANDROID));
    }

    @Test
    @DisplayName("出場者にはコート番号つきで「あなたの試合です」を送る")
    void notifiesPlayingParticipantWithCourt() {
        subscribe("ExponentPushToken[court2playerxxxxxxxxx]", players.get(5));

        service.notifySetStarted(room.id(), 1);

        assertThat(sender.sent).hasSize(1);
        PushMessage message = sender.sent.get(0);
        assertThat(message.title()).isEqualTo("あなたの試合です");
        assertThat(message.body()).contains("第1セット").contains("コート2").contains("金曜練");
        // タップでルームを開けるよう roomId を載せる
        assertThat(message.data()).containsEntry("roomId", room.id().value().toString());
    }

    @Test
    @DisplayName("番号を申告済みで出場しない人には休憩と伝える")
    void notifiesRestingParticipant() {
        subscribe("ExponentPushToken[restingplayerxxxxxxxx]", ParticipantId.newId());

        service.notifySetStarted(room.id(), 1);

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.get(0).title()).isEqualTo("第1セットが始まりました");
        assertThat(sender.sent.get(0).body()).contains("休憩");
    }

    @Test
    @DisplayName("番号を申告していない端末には汎用の文面を送る")
    void notifiesAnonymousDevice() {
        subscribe("ExponentPushToken[anonymousdevicexxxxxx]", null);

        service.notifySetStarted(room.id(), 1);

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.get(0).title()).isEqualTo("第1セットが始まりました");
        assertThat(sender.sent.get(0).body()).isEqualTo("金曜練");
    }

    @Test
    @DisplayName("購読が無ければ何も送らない")
    void sendsNothingWithoutSubscriptions() {
        service.notifySetStarted(room.id(), 1);

        assertThat(sender.sent).isEmpty();
    }

    @Test
    @DisplayName("ルーム終了を通知し、以後変化しないので購読を掃除する")
    void notifiesRoomClosedAndClearsSubscriptions() {
        subscribe("ExponentPushToken[closingdevicexxxxxxxx]", players.get(0));

        service.notifyRoomClosed(room.id());

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.get(0).title()).isEqualTo("お疲れさまでした");
        assertThat(sender.sent.get(0).body()).contains("金曜練");
        assertThat(subscriptions.findByRoomId(room.id())).isEmpty();
    }

    private static class RecordingSender implements PushNotificationSender {
        private final List<PushMessage> sent = new ArrayList<>();

        @Override
        public void send(List<PushMessage> messages) {
            sent.addAll(messages);
        }
    }

    private static class FakeSubscriptions implements PushSubscriptionRepository {
        private final Map<String, PushSubscription> store = new HashMap<>();

        private String key(RoomId roomId, ExpoPushToken token) {
            return roomId.value() + "|" + token.value();
        }

        @Override
        public void save(PushSubscription subscription) {
            store.put(key(subscription.roomId(), subscription.token()), subscription);
        }

        @Override
        public List<PushSubscription> findByRoomId(RoomId roomId) {
            return store.values().stream().filter(s -> s.roomId().equals(roomId)).toList();
        }

        @Override
        public void delete(RoomId roomId, ExpoPushToken token) {
            store.remove(key(roomId, token));
        }

        @Override
        public void deleteByToken(ExpoPushToken token) {
            store.values().removeIf(s -> s.token().equals(token));
        }
    }

    private static class FakeSchedules implements MatchScheduleRepository {
        private final Map<UUID, MatchSchedule> store = new HashMap<>();

        @Override
        public MatchSchedule save(MatchSchedule schedule) {
            store.put(schedule.roomId().value(), schedule);
            return schedule;
        }

        @Override
        public Optional<MatchSchedule> findByRoomId(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId.value()));
        }

        @Override
        public void deleteByRoomId(RoomId roomId) {
            store.remove(roomId.value());
        }

        @Override
        public Optional<MatchSchedule> startSet(
                RoomId roomId, int setNumber, OffsetDateTime startedAt) {
            return findByRoomId(roomId);
        }
    }

    private static class FakeRooms implements RoomRepository {
        private final Map<UUID, Room> store = new HashMap<>();

        @Override
        public Room save(Room room) {
            store.put(room.id().value(), room);
            return room;
        }

        @Override
        public void deleteById(RoomId roomId) {
            store.remove(roomId.value());
        }

        @Override
        public Optional<Room> findById(RoomId roomId) {
            return Optional.ofNullable(store.get(roomId.value()));
        }

        @Override
        public Optional<Room> findByShareCode(String shareCode) {
            return Optional.empty();
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return List.of();
        }

        @Override
        public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return List.of();
        }

        @Override
        public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
            return 0;
        }

        @Override
        public List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore) {
            return List.of();
        }
    }
}
