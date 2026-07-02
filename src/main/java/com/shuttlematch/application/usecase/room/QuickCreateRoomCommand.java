package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.user.UserId;

/**
 * かんたんルーム作成の入力。番号(1..participantCount)の参加者を登録し、試合表まで生成する。
 */
public record QuickCreateRoomCommand(
        String title,
        int courtCount,
        int participantCount,
        UserId createdBy) {
}
