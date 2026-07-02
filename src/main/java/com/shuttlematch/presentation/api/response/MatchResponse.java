package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.match.Match;

import java.time.OffsetDateTime;

/**
 * 1試合のレスポンス表現。startedAt はセット開始時刻(未開始は null)。
 */
public record MatchResponse(int matchNumber, int setNumber, PairResponse pairA, PairResponse pairB,
                            Integer courtNumber, OffsetDateTime startedAt) {

    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.matchNumber().value(),
                match.setNumber(),
                PairResponse.from(match.pairA()),
                PairResponse.from(match.pairB()),
                match.courtNumber(),
                match.startedAt());
    }
}
