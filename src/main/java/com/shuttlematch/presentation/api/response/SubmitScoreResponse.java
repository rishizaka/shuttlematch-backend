package com.shuttlematch.presentation.api.response;

import com.shuttlematch.application.usecase.game.SubmitScoreResult;

/**
 * スコア登録のレスポンス。
 *
 * @param rankedIn ランクインしたか
 * @param rank     ランクインしたときの順位(1〜5)。しなかったときは null
 * @param ranking  登録後のランキング
 */
public record SubmitScoreResponse(boolean rankedIn, Integer rank, RankingResponse ranking) {

    public static SubmitScoreResponse from(SubmitScoreResult result) {
        return new SubmitScoreResponse(
                result.rankedIn(),
                result.rank().orElse(null),
                RankingResponse.from(result.ranking()));
    }
}
