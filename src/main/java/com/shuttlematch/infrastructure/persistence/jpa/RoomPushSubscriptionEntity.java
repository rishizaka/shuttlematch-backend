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
 * room_push_subscriptions テーブルに対応する JPA エンティティ。
 * 「このルームの通知をこの端末(Expo Push Token)に送る」という購読。
 */
@Entity
@Table(name = "room_push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class RoomPushSubscriptionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "expo_token", nullable = false)
    private String expoToken;

    /** 端末が自己申告した「自分の番号」の参加者 ID。未設定なら null。 */
    @Column(name = "participant_id")
    private UUID participantId;

    @Column(name = "platform", nullable = false)
    private String platform;

    // DB の default now() に任せる
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
