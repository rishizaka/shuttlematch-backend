package com.shuttlematch.infrastructure.persistence.jpa;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomJpaRepository
        extends JpaRepository<RoomEntity, UUID>, JpaSpecificationExecutor<RoomEntity> {

    List<RoomEntity> findByStatusOrderByHeldAtAsc(String status);

    Optional<RoomEntity> findByShareCode(String shareCode);

    long countByCreatedByAndCreatedAtGreaterThanEqual(UUID createdBy, OffsetDateTime since);

    List<RoomEntity> findByStatusNotAndCreatedAtBefore(String status, OffsetDateTime createdAt);

    /**
     * 参加者の端末で開かれた回数を1増やし、初回なら日時を記録する(KPI 用。V20 参照)。
     * 列はエンティティに載せていないので native の UPDATE で直接更新する。
     *
     * @return 更新した行数(ルームが無ければ 0)
     */
    @Modifying
    @Query(
            value = "update rooms set participant_open_count = participant_open_count + 1, "
                    + "participant_first_opened_at = coalesce(participant_first_opened_at, now()) "
                    + "where id = :roomId",
            nativeQuery = true)
    int incrementParticipantOpen(@Param("roomId") UUID roomId);
}
