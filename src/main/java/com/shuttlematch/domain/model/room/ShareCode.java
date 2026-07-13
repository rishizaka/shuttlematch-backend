package com.shuttlematch.domain.model.room;

import java.security.SecureRandom;

/**
 * ルームの共有コードの生成。短いURL(/r/{code})での共有に使う。
 * 紛らわしい文字(0/O、1/l/I など)を除いた英数字8文字。
 * 31^8 ≒ 8500億通りのため実運用での衝突は考慮しない(DBのユニーク制約が最終防衛線)。
 */
public final class ShareCode {

    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareCode() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
