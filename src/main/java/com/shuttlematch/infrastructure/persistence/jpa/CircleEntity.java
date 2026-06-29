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
 * circles テーブルに対応する JPA エンティティ。
 */
@Entity
@Table(name = "circles")
@Getter
@Setter
@NoArgsConstructor
public class CircleEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "join_policy", nullable = false)
    private String joinPolicy;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
