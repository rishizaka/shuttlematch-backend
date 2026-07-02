package com.shuttlematch.domain.model.room;

import com.shuttlematch.domain.model.user.UserId;

import java.util.Objects;

/**
 * セッション参加者(エンティティ)。登録ユーザーまたはゲスト(名前のみ)のいずれか。
 */
public record Participant(ParticipantId id, UserId userId, String guestName, ParticipantStatus status) {

    public Participant {
        Objects.requireNonNull(id, "ParticipantId は null にできません");
        Objects.requireNonNull(status, "status は null にできません");
        boolean hasUser = userId != null;
        boolean hasGuest = guestName != null && !guestName.isBlank();
        if (hasUser == hasGuest) {
            throw new IllegalArgumentException("登録ユーザーまたはゲスト名のいずれか一方を指定してください");
        }
    }

    /** 登録ユーザーの参加者を新規に作る。 */
    public static Participant ofUser(UserId userId) {
        return new Participant(ParticipantId.newId(), userId, null, ParticipantStatus.ACTIVE);
    }

    /** ゲスト参加者を新規に作る。 */
    public static Participant ofGuest(String guestName) {
        return new Participant(ParticipantId.newId(), null, guestName, ParticipantStatus.ACTIVE);
    }

    /** 永続化層からの復元用(状態を指定)。 */
    public static Participant reconstitute(
            ParticipantId id, UserId userId, String guestName, ParticipantStatus status) {
        return new Participant(id, userId, guestName, status);
    }

    /** 状態を変えた新しいインスタンスを返す。 */
    public Participant withStatus(ParticipantStatus newStatus) {
        return new Participant(id, userId, guestName, newStatus);
    }

    /** ゲスト名(ニックネーム)を変えた新しいインスタンスを返す。ゲストのみ変更可。 */
    public Participant withGuestName(String newGuestName) {
        if (!isGuest()) {
            throw new IllegalStateException("登録ユーザーの名前は変更できません");
        }
        return new Participant(id, null, newGuestName, status);
    }

    public boolean isGuest() {
        return userId == null;
    }

    public boolean isActive() {
        return status == ParticipantStatus.ACTIVE;
    }
}
