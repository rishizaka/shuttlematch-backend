package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.match.Pair;

/**
 * ペア(2名)のレスポンス表現。
 */
public record PairResponse(String player1Id, String player2Id) {

    public static PairResponse from(Pair pair) {
        return new PairResponse(
                pair.player1().value().toString(),
                pair.player2().value().toString());
    }
}
