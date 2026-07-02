package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.user.UserId;

/**
 * かんたんセッション作成の入力。番号(1..participantCount)の参加者を登録し、試合表まで生成する。
 */
public record QuickCreateSessionCommand(
        CircleId circleId,
        String title,
        int courtCount,
        int participantCount,
        UserId createdBy) {
}
