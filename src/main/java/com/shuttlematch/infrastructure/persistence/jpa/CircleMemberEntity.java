package com.shuttlematch.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * circle_members テーブルに対応する JPA エンティティ(複合主キー)。
 */
@Entity
@Table(name = "circle_members")
@IdClass(CircleMemberId.class)
@Getter
@Setter
@NoArgsConstructor
public class CircleMemberEntity {

    @Id
    @Column(name = "circle_id", nullable = false)
    private UUID circleId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private OffsetDateTime joinedAt;
}
