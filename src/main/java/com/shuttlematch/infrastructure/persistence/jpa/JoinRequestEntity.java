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
 * circle_join_requests テーブルに対応する JPA エンティティ。
 */
@Entity
@Table(name = "circle_join_requests")
@Getter
@Setter
@NoArgsConstructor
public class JoinRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
