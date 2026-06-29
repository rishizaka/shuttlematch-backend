package com.shuttlematch.domain.model.session;

import com.shuttlematch.domain.model.user.UserId;

import java.util.Objects;

/**
 * セッション参加者(エンティティ)。登録ユーザーまたはゲスト(名前のみ)のいずれか。
 */
public record Participant(ParticipantId id, UserId userId, String guestName) {

    public Participant {
        Objects.requireNonNull(id, "ParticipantId は null にできません");
        boolean hasUser = userId != null;
        boolean hasGuest = guestName != null && !guestName.isBlank();
        if (hasUser == hasGuest) {
            throw new IllegalArgumentException("登録ユーザーまたはゲスト名のいずれか一方を指定してください");
        }
    }

    /** 登録ユーザーの参加者を新規に作る。 */
    public static Participant ofUser(UserId userId) {
        return new Participant(ParticipantId.newId(), userId, null);
    }

    /** ゲスト参加者を新規に作る。 */
    public static Participant ofGuest(String guestName) {
        return new Participant(ParticipantId.newId(), null, guestName);
    }

    /** 永続化層からの復元用。 */
    public static Participant reconstitute(ParticipantId id, UserId userId, String guestName) {
        return new Participant(id, userId, guestName);
    }

    public boolean isGuest() {
        return userId == null;
    }
}
