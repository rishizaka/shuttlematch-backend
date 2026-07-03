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
 * users テーブルに対応する JPA エンティティ。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "cognito_sub")
    private String cognitoSub;

    @Column(name = "name", nullable = false)
    private String name;

    /** ゲストユーザーは null。 */
    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
