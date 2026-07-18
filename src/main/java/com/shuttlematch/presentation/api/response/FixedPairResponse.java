package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.match.Pair;

/**
 * 固定ペアのレスポンス表現(参加者 ID の組)。
 */
public record FixedPairResponse(String participantA, String participantB) {

    public static FixedPairResponse from(Pair pair) {
        return new FixedPairResponse(
                pair.player1().value().toString(),
                pair.player2().value().toString());
    }
}
