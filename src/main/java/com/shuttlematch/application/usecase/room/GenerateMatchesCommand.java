package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.service.MatchingDomainService;

/**
 * 試合生成ユースケースの入力。
 */
public record GenerateMatchesCommand(RoomId roomId, int matchCount) {

    /** セット数を省略した場合はデフォルト(10セット)を使う。 */
    public GenerateMatchesCommand(RoomId roomId) {
        this(roomId, MatchingDomainService.DEFAULT_SET_COUNT);
    }
}
