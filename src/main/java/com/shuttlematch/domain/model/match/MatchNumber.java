package com.shuttlematch.domain.model.match;

/**
 * 試合番号(値オブジェクト)。1始まり。
 */
public record MatchNumber(int value) {

    public MatchNumber {
        if (value < 1) {
            throw new IllegalArgumentException("MatchNumber は 1 以上である必要があります: " + value);
        }
    }

    public static MatchNumber of(int value) {
        return new MatchNumber(value);
    }
}
