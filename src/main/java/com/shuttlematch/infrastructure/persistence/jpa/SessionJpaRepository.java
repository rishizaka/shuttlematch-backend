package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, UUID> {

    List<SessionEntity> findByStatusOrderByHeldAtAsc(String status);
}
