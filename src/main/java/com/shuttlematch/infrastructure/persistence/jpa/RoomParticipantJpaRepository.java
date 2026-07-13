package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomParticipantJpaRepository extends JpaRepository<RoomParticipantEntity, UUID> {

    List<RoomParticipantEntity> findByRoomId(UUID roomId);

    /**
     * 参加順で取得する。参加者番号は一覧の並び順で決まるため、
     * 集約の復元では必ずこちらを使う(順序未指定だと取得のたびに並びが変わる)。
     */
    List<RoomParticipantEntity> findByRoomIdOrderByJoinOrderAsc(UUID roomId);
}
