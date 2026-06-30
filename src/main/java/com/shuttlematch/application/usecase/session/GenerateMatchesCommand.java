package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.service.MatchingDomainService;

/**
 * 試合生成ユースケースの入力。
 */
public record GenerateMatchesCommand(SessionId sessionId, int matchCount) {

    /** セット数を省略した場合はデフォルト(10セット)を使う。 */
    public GenerateMatchesCommand(SessionId sessionId) {
        this(sessionId, MatchingDomainService.DEFAULT_SET_COUNT);
    }
}
