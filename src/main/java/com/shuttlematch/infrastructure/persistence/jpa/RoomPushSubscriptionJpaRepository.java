package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomPushSubscriptionJpaRepository
        extends JpaRepository<RoomPushSubscriptionEntity, UUID> {

    List<RoomPushSubscriptionEntity> findByRoomId(UUID roomId);

    Optional<RoomPushSubscriptionEntity> findByRoomIdAndExpoToken(UUID roomId, String expoToken);

    void deleteByRoomIdAndExpoToken(UUID roomId, String expoToken);

    void deleteByExpoToken(String expoToken);
}
