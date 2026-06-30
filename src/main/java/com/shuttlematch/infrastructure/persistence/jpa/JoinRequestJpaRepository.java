package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestJpaRepository extends JpaRepository<JoinRequestEntity, UUID> {

    List<JoinRequestEntity> findByCircleIdAndStatusOrderByRequestedAtAsc(UUID circleId, String status);

    boolean existsByCircleIdAndUserIdAndStatus(UUID circleId, UUID userId, String status);
}
