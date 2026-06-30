package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.SessionVisibility;
import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;

/**
 * セッション作成ユースケースの入力。
 */
public record CreateSessionCommand(
        CircleId circleId,
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        SessionVisibility visibility,
        UserId createdBy) {
}
