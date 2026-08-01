package com.shuttlematch.domain.model.game;

import java.util.Objects;

/**
 * ランキングに載せるプレイヤー名(値オブジェクト)。
 *
 * <p>誰でも登録でき、全員に見える文字列なので、ここで長さと文字種を絞る。
 * 前後の空白は落とし、改行やタブなどの制御文字は名前に含めない。
 * 全角ひらがな・漢字・絵文字は許可する(日本語のニックネームで使うため)。
 */
public record PlayerName(String value) {

    /** 最大文字数。ランキング表の1行に収まる長さ。 */
    public static final int MAX_LENGTH = 8;

    public PlayerName {
        Objects.requireNonNull(value, "value は null にできません");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("名前を入力してください");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("名前は " + MAX_LENGTH + " 文字までです");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("名前に改行や制御文字は使えません");
        }
    }

    public static PlayerName of(String value) {
        return new PlayerName(value);
    }
}
