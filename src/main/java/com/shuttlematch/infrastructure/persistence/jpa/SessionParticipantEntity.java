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
 * session_participants テーブルに対応する JPA エンティティ。
 */
@Entity
@Table(name = "session_participants")
@Getter
@Setter
@NoArgsConstructor
public class SessionParticipantEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_name")
    private String guestName;

    // DB の default now() に任せるため書き込みはしない
    @Column(name = "joined_at", insertable = false, updatable = false)
    private OffsetDateTime joinedAt;
}
