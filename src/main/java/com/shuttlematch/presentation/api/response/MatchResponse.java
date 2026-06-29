package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.match.Match;

/**
 * 1試合のレスポンス表現。
 */
public record MatchResponse(int matchNumber, PairResponse pairA, PairResponse pairB, Integer courtNumber) {

    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.matchNumber().value(),
                PairResponse.from(match.pairA()),
                PairResponse.from(match.pairB()),
                match.courtNumber());
    }
}
