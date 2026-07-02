package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchScheduleJpaRepository extends JpaRepository<MatchScheduleEntity, UUID> {

    @EntityGraph(attributePaths = "matches")
    Optional<MatchScheduleEntity> findByRoomId(UUID roomId);

    // DB の ON DELETE CASCADE により matches も削除される。
    // 一括削除なので後続の insert より先に DB へ反映される。
    @Modifying
    @Query("delete from MatchScheduleEntity e where e.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") UUID roomId);
}
