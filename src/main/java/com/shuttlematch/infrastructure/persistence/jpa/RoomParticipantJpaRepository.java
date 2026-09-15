package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomParticipantJpaRepository extends JpaRepository<RoomParticipantEntity, UUID> {

    List<RoomParticipantEntity> findByRoomId(UUID roomId);

    /**
     * 参加順で取得する。参加者番号は一覧の並び順で決まるため、
     * 集約の復元では必ずこちらを使う(順序未指定だと取得のたびに並びが変わる)。
     */
    List<RoomParticipantEntity> findByRoomIdOrderByJoinOrderAsc(UUID roomId);

    /**
     * 「番号のまま(まだ誰も名乗っていない)」枠のうち、番号が一番若い1行を行ロックして返す
     * ({@code FOR UPDATE SKIP LOCKED})。同時に複数リクエストが呼んでも、それぞれ別の行が
     * ロックされるため取り合いにならない(同じ番号を2人が同時に名乗ってしまう事故を防ぐ)。
     * <p>
     * 呼び出し側は同じトランザクション内でこの行を更新すること(ロックはトランザクション終了まで
     * 保持される)。{@link RoomRepositoryAdapter#claimNextFreeSlot} 参照。
     */
    @Query(
            value = "select * from room_participants "
                    + "where room_id = :roomId and status = 'ACTIVE' and guest_name ~ '^[0-9]+$' "
                    + "order by join_order asc limit 1 for update skip locked",
            nativeQuery = true)
    Optional<RoomParticipantEntity> lockNextFreeSlot(@Param("roomId") UUID roomId);
}
