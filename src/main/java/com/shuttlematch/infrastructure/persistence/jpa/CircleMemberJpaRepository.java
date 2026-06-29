package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleMemberJpaRepository extends JpaRepository<CircleMemberEntity, CircleMemberId> {

    List<CircleMemberEntity> findByCircleId(UUID circleId);
}
