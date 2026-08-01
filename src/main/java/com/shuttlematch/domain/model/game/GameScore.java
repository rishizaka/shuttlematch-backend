package com.shuttlematch.domain.model.game;

import java.time.Instant;
import java.util.Objects;

/**
 * ランキングに記録された 1 件のスコア。
 *
 * <p>スコアの妥当性(0 以上・ゲームごとの上限以下)はここで守る。認証が無いぶん、
 * 「そのゲームとしてありえない値」はドメインに入る前に弾く。
 *
 * @param game       どのミニゲームのスコアか
 * @param playerName プレイヤーが自分で入れた名前
 * @param score      スコア
 * @param recordedAt 記録した時刻(同点のときは早い方が上位)
 */
public record GameScore(MiniGame game, PlayerName playerName, int score, Instant recordedAt) {

    public GameScore {
        Objects.requireNonNull(game, "game は null にできません");
        Objects.requireNonNull(playerName, "playerName は null にできません");
        Objects.requireNonNull(recordedAt, "recordedAt は null にできません");
        if (score < 0) {
            throw new IllegalArgumentException("スコアは 0 以上です: " + score);
        }
        if (score > game.maxScore()) {
            throw new IllegalArgumentException(
                    "スコアが上限を超えています: " + score + " (上限 " + game.maxScore() + ")");
        }
    }
}
