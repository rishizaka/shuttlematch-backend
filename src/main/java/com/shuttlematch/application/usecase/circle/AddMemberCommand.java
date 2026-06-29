package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.MemberRole;
import com.shuttlematch.domain.model.user.UserId;

/**
 * メンバー追加ユースケースの入力。
 */
public record AddMemberCommand(CircleId circleId, UserId userId, MemberRole role) {
}
