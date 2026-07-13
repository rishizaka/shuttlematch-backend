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
 * room_participants テーブルに対応する JPA エンティティ。
 */
@Entity
@Table(name = "room_participants")
@Getter
@Setter
@NoArgsConstructor
public class RoomParticipantEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "status", nullable = false)
    private String status;

    // DB の default now() に任せるため書き込みはしない
    @Column(name = "joined_at", insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    // 参加順の連番(DB のシーケンス既定値に任せる)。参加者番号の並び順の基準。
    @Column(name = "join_order", insertable = false, updatable = false)
    private Long joinOrder;
}
