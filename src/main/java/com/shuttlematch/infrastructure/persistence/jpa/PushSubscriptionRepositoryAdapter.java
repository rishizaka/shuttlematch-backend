package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PushSubscriptionRepository} の JPA 実装。
 */
@Repository
public class PushSubscriptionRepositoryAdapter implements PushSubscriptionRepository {

    private final RoomPushSubscriptionJpaRepository jpaRepository;

    public PushSubscriptionRepositoryAdapter(RoomPushSubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(PushSubscription subscription) {
        UUID roomId = subscription.roomId().value();
        String token = subscription.token().value();
        // 同じ端末が同じルームを再登録したら更新する(「自分の番号」を後から設定する場合など)。
        RoomPushSubscriptionEntity entity = jpaRepository
                .findByRoomIdAndExpoToken(roomId, token)
                .orElseGet(() -> {
                    RoomPushSubscriptionEntity created = new RoomPushSubscriptionEntity();
                    created.setId(UUID.randomUUID());
                    created.setRoomId(roomId);
                    created.setExpoToken(token);
                    return created;
                });
        entity.setParticipantId(subscription.participantId().map(ParticipantId::value).orElse(null));
        entity.setPlatform(subscription.platform().name().toLowerCase(Locale.ROOT));
        entity.setUpdatedAt(OffsetDateTime.now());
        jpaRepository.save(entity);
    }

    @Override
    public List<PushSubscription> findByRoomId(RoomId roomId) {
        return jpaRepository.findByRoomId(roomId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(RoomId roomId, ExpoPushToken token) {
        jpaRepository.deleteByRoomIdAndExpoToken(roomId.value(), token.value());
    }

    @Override
    @Transactional
    public void deleteByToken(ExpoPushToken token) {
        jpaRepository.deleteByExpoToken(token.value());
    }

    private PushSubscription toDomain(RoomPushSubscriptionEntity entity) {
        return new PushSubscription(
                RoomId.of(entity.getRoomId()),
                ExpoPushToken.of(entity.getExpoToken()),
                Optional.ofNullable(entity.getParticipantId()).map(ParticipantId::of),
                PushPlatform.valueOf(entity.getPlatform().toUpperCase(Locale.ROOT)));
    }
}
