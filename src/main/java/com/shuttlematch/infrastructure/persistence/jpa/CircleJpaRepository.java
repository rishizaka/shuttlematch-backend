package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleJpaRepository extends JpaRepository<CircleEntity, UUID> {
}
