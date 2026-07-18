package com.shuttlematch.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * room_fixed_pairs テーブルに対応する JPA エンティティ。
 * participant_a < participant_b に正規化された固定ペア(常に同じチームで組む2人)。
 */
@Entity
@Table(name = "room_fixed_pairs")
@Getter
@Setter
@NoArgsConstructor
public class RoomFixedPairEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "participant_a", nullable = false)
    private UUID participantA;

    @Column(name = "participant_b", nullable = false)
    private UUID participantB;

    // DB の default now() に任せる
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
