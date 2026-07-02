package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomParticipantJpaRepository extends JpaRepository<RoomParticipantEntity, UUID> {

    List<RoomParticipantEntity> findByRoomId(UUID roomId);
}
