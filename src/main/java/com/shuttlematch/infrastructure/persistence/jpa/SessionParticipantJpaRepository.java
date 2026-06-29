package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantJpaRepository extends JpaRepository<SessionParticipantEntity, UUID> {

    List<SessionParticipantEntity> findBySessionId(UUID sessionId);
}
