package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomOpenRepository;

import org.springframework.stereotype.Repository;

/**
 * {@link RoomOpenRepository} の JPA 実装。rooms の計測列だけを1行の UPDATE で更新する
 * (エンティティには載せていない。V20 参照)。
 */
@Repository
public class RoomOpenRepositoryAdapter implements RoomOpenRepository {

    private final RoomJpaRepository roomJpaRepository;

    public RoomOpenRepositoryAdapter(RoomJpaRepository roomJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
    }

    @Override
    public boolean recordParticipantOpen(RoomId roomId) {
        return roomJpaRepository.incrementParticipantOpen(roomId.value()) > 0;
    }
}
