package com.shuttlematch.application.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
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

class RoomPushSubscriptionUseCaseTest {

    private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";

    private FakePushSubscriptionRepository subscriptions;
    private FakeRoomRepository rooms;
    private SubscribeRoomPushUseCase subscribe;
    private UnsubscribeRoomPushUseCase unsubscribe;
    private Room room;

    @BeforeEach
    void setUp() {
        subscriptions = new FakePushSubscriptionRepository();
        rooms = new FakeRoomRepository();
        subscribe = new SubscribeRoomPushUseCase(subscriptions, rooms);
        unsubscribe = new UnsubscribeRoomPushUseCase(subscriptions);
        room = Room.create("テスト", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        rooms.save(room);
    }

    @Test
    @DisplayName("購読を登録できる")
    void subscribes() {
        subscribe.execute(
                room.id(), ExpoPushToken.of(TOKEN), Optional.empty(), PushPlatform.ANDROID);

        List<PushSubscription> saved = subscriptions.findByRoomId(room.id());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).token().value()).isEqualTo(TOKEN);
        assertThat(saved.get(0).participantId()).isEmpty();
        assertThat(saved.get(0).platform()).isEqualTo(PushPlatform.ANDROID);
    }

    @Test
    @DisplayName("同じ端末の再登録は上書きされ、自分の番号を後から付けられる")
    void reSubscribeOverwritesParticipant() {
        ParticipantId me = ParticipantId.newId();
        subscribe.execute(
                room.id(), ExpoPushToken.of(TOKEN), Optional.empty(), PushPlatform.ANDROID);
        subscribe.execute(
                room.id(), ExpoPushToken.of(TOKEN), Optional.of(me), PushPlatform.ANDROID);

        List<PushSubscription> saved = subscriptions.findByRoomId(room.id());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).participantId()).contains(me);
    }

    @Test
    @DisplayName("存在しないルームは購読できない")
    void rejectsUnknownRoom() {
        RoomId unknown = RoomId.of(UUID.randomUUID());
        assertThatThrownBy(() -> subscribe.execute(
                unknown, ExpoPushToken.of(TOKEN), Optional.empty(), PushPlatform.IOS))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("購読を解除できる。存在しない購読の解除は何もしない(冪等)")
    void unsubscribes() {
        subscribe.execute(
                room.id(), ExpoPushToken.of(TOKEN), Optional.empty(), PushPlatform.ANDROID);

        unsubscribe.execute(room.id(), ExpoPushToken.of(TOKEN));
        assertThat(subscriptions.findByRoomId(room.id())).isEmpty();

        unsubscribe.execute(room.id(), ExpoPushToken.of(TOKEN));
        assertThat(subscriptions.findByRoomId(room.id())).isEmpty();
    }

    @Test
    @DisplayName("Expo の形式でないトークンは受け付けない")
    void rejectsInvalidToken() {
        assertThatThrownBy(() -> ExpoPushToken.of("fcm-raw-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** ルーム購読のインメモリ実装(キーは (roomId, token))。 */
    private static class FakePushSubscriptionRepository implements PushSubscriptionRepository {
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
            List<PushSubscription> result = new ArrayList<>();
            for (PushSubscription s : store.values()) {
                if (s.roomId().equals(roomId)) {
                    result.add(s);
                }
            }
            return result;
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

    /** 購読ユースケースが使うのはルームの存在確認だけなので、最小限のフェイク。 */
    private static class FakeRoomRepository implements RoomRepository {
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
            return store.values().stream().filter(r -> r.shareCode().equals(shareCode)).findFirst();
        }

        @Override
        public List<Room> findByStatus(RoomStatus status) {
            return store.values().stream().filter(r -> r.status() == status).toList();
        }

        @Override
        public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
            return List.copyOf(store.values());
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
