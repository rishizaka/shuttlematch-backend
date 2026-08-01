package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ミニゲームのスコア登録リクエスト。
 *
 * @param playerName プレイヤーが自分で入れた名前(最大8文字)
 * @param score      スコア。上限はゲームごとにドメイン側で弾く
 */
public record SubmitScoreRequest(
        @NotBlank @Size(max = 8, message = "名前は8文字までです") String playerName,
        @Min(value = 0, message = "スコアは0以上です") int score) {
}
