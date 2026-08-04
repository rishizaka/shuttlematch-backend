package com.shuttlematch.domain.model.game;

import java.util.Arrays;
import java.util.Locale;

/**
 * ランキングを持つミニゲーム。
 *
 * <p>{@code slug} はフロントエンドの URL (/game/{slug}) および API のパス変数と同じ値。
 * ゲームを増やすときはここに 1 行足せば API とランキングが揃う。
 *
 * <p>{@code maxScore} は「人間が出しうる値をはるかに超えたスコアを弾く」ためだけの上限。
 * 認証が無く誰でも登録できるので、桁違いの値(int の最大値など)で荒らされるのを防ぐ。
 * 加点が 1〜50 点の 4 本(flap/rain/coin/flick)はもちろん、滑走距離に倍率を掛ける
 * ski でも、20 分以上ノーミスで滑り続けないと到達しない(フロント側でも頭打ちにしてある)。
 */
public enum MiniGame {
    FLAP("flap", 99_999),
    RAIN("rain", 99_999),
    COIN("coin", 99_999),
    FLICK("flick", 99_999),
    SKI("ski", 99_999);

    private final String slug;
    private final int maxScore;

    MiniGame(String slug, int maxScore) {
        this.slug = slug;
        this.maxScore = maxScore;
    }

    public String slug() {
        return slug;
    }

    public int maxScore() {
        return maxScore;
    }

    /** URL のスラッグ(flap / rain / coin / flick / ski)から解決する。未知の値は例外。 */
    public static MiniGame fromSlug(String slug) {
        String normalized = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(game -> game.slug.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不明なミニゲームです: " + slug));
    }
}
