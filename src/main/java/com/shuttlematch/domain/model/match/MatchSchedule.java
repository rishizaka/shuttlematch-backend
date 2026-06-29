package com.shuttlematch.domain.model.match;

import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;

import java.util.List;
import java.util.Objects;

/**
 * 試合スケジュール(集約ルート)。1セッションの全試合を保持する。
 */
public record MatchSchedule(SessionId sessionId, List<Match> matches) {

    public MatchSchedule {
        Objects.requireNonNull(sessionId, "sessionId は null にできません");
        Objects.requireNonNull(matches, "matches は null にできません");
        matches = List.copyOf(matches); // 防御的コピー + 不変化
    }

    public int size() {
        return matches.size();
    }

    /** 指定参加者が出場する試合のみを抽出する。 */
    public List<Match> matchesOf(ParticipantId participant) {
        return matches.stream()
                .filter(match -> match.hasParticipant(participant))
                .toList();
    }
}
