package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.user.UserId;

/**
 * 参加登録ユースケースの入力。登録ユーザー(userId)またはゲスト(guestName)のいずれか一方を指定する。
 */
public record AddParticipantCommand(SessionId sessionId, UserId userId, String guestName) {

    public AddParticipantCommand {
        boolean hasUser = userId != null;
        boolean hasGuest = guestName != null && !guestName.isBlank();
        if (hasUser == hasGuest) {
            throw new IllegalArgumentException("登録ユーザーまたはゲスト名のいずれか一方を指定してください");
        }
    }

    public boolean isGuest() {
        return userId == null;
    }
}
