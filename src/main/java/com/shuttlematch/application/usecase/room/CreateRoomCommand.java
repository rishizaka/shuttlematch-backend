package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;

/**
 * ルーム作成ユースケースの入力。
 */
public record CreateRoomCommand(
        String title,
        OffsetDateTime heldAt,
        String location,
        Integer capacity,
        Integer courtCount,
        UserId createdBy) {
}
