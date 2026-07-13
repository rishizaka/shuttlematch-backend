package com.shuttlematch.infrastructure.persistence.jpa;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomJpaRepository
        extends JpaRepository<RoomEntity, UUID>, JpaSpecificationExecutor<RoomEntity> {

    List<RoomEntity> findByStatusOrderByHeldAtAsc(String status);

    Optional<RoomEntity> findByShareCode(String shareCode);

    long countByCreatedByAndCreatedAtGreaterThanEqual(UUID createdBy, OffsetDateTime since);

    List<RoomEntity> findByStatusNotAndCreatedAtBefore(String status, OffsetDateTime createdAt);
}
