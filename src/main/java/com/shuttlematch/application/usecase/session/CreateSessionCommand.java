package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;

/**
 * ルーム作成ユースケースの入力。
 */
public record CreateSessionCommand(
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        UserId createdBy) {
}
