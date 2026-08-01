package com.shuttlematch.application.usecase.game;

import com.shuttlematch.domain.model.game.Ranking;

import java.util.Optional;

/**
 * スコア登録の結果。
 *
 * @param rank    ランクインしたときの順位(1〜5)。ランクインしなければ空
 * @param ranking 登録後のランキング(ランクインしなかった場合は現在のもの)
 */
public record SubmitScoreResult(Optional<Integer> rank, Ranking ranking) {

    public boolean rankedIn() {
        return rank.isPresent();
    }
}
