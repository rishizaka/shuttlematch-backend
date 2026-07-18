package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomFixedPairJpaRepository extends JpaRepository<RoomFixedPairEntity, UUID> {

    List<RoomFixedPairEntity> findByRoomId(UUID roomId);
}
