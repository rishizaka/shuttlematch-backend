package com.shuttlematch.domain.model.room;

import com.shuttlematch.domain.model.user.UserId;

import java.util.Objects;

/**
 * セッション参加者(エンティティ)。登録ユーザーまたはゲスト(名前のみ)のいずれか。
 */
public record Participant(ParticipantId id, UserId userId, String guestName, ParticipantStatus status) {

    /**
     * 運営者が「番号とユーザーの紐付けを解いた」フリー枠の名前。
     * フロントの FREE_SLOT(src/lib/guests.ts)と同じ文字列。誰でも名乗ってよい。
     */
    public static final String FREE_SLOT = "フリー";

    /**
     * 運営者が代理追加する遅刻者・ビジターの既定名。
     * フロントの VISITOR_PLACEHOLDER(src/lib/guests.ts)と同じ文字列。誰でも名乗ってよい。
     */
    public static final String VISITOR_PLACEHOLDER = "遅刻者・ビジター";

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

    /**
     * 誰でも名乗ってよい「空き」枠か(番号のまま / フリー / 遅刻者・ビジターの既定名)。
     * フロントの isClaimableSlot(src/lib/guests.ts)と同じ判定。
     * {@link com.shuttlematch.domain.repository.RoomRepository#claimNextFreeSlot} が
     * 自動採番の対象を選ぶのに使う。
     */
    public boolean isClaimableSlot() {
        if (guestName == null) return true;
        String t = guestName.trim();
        return t.matches("\\d+") || t.equals(VISITOR_PLACEHOLDER) || t.equals(FREE_SLOT);
    }
}
